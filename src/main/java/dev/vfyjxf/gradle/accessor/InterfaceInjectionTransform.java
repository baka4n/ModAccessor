package dev.vfyjxf.gradle.accessor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;
import org.objectweb.asm.util.CheckSignatureAdapter;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static java.util.stream.StreamSupport.stream;

public abstract class InterfaceInjectionTransform implements TransformAction<InterfaceInjectionTransform.Parameters> {

    /**
     * {@link  org.gradle.api.internal.file.archive.ZipCopyAction#CONSTANT_TIME_FOR_ZIP_ENTRIES}
     */
    private static final long CONSTANT_TIME_FOR_ZIP_ENTRIES = new GregorianCalendar(1980, Calendar.FEBRUARY, 1, 0, 0, 0).getTimeInMillis();


    @Inject
    public InterfaceInjectionTransform() {}

    public interface Parameters extends TransformParameters {
        // Define any parameters you need for the transform
        @InputFiles
        @PathSensitive(PathSensitivity.NONE)
        ConfigurableFileCollection getInterfaceInjectionFiles();
    }

    @InputArtifact
    @PathSensitive(PathSensitivity.NONE)
    public abstract Provider<FileSystemLocation> getInputArtifact();

    @Override
    public void transform(@NotNull TransformOutputs outputs) {
        try {
            File artifact = getInputArtifact().get().getAsFile();
            if (!artifact.exists()) return;
            Parameters parameters = getParameters();
            List<Path> injectionFilePaths = stream(parameters.getInterfaceInjectionFiles().spliterator(), false)
                    .map(File::toPath)
                    .toList();
            Map<String, List<String>> injections = injectionFilePaths.stream()
                    .flatMap(path -> {
                        try {
                            JsonObject jsonObject = JsonParser.parseReader(Files.newBufferedReader(path)).getAsJsonObject();
                            return jsonObject.entrySet().stream();
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to parse injection file: " + path, e);
                        }
                    })
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> {
                                JsonElement value = entry.getValue();
                                if (value.isJsonArray()) {
                                    return stream(value.getAsJsonArray().spliterator(), false)
                                            .map(JsonElement::getAsString)
                                            .collect(Collectors.toList());
                                } else if (value.isJsonPrimitive()) {
                                    return Collections.singletonList(value.getAsString());
                                }
                                return Collections.emptyList();
                            },
                            (list1, list2) -> {
                                return new ArrayList<String>(list2);
                            }
                    ));

            File outputFile = outputs.file("injected-"+artifact.getName());
            if (injections.isEmpty()) {
                try {
                    Files.copy(artifact.toPath(), outputFile.toPath());
                } catch (IOException e) {throw new RuntimeException(e);}
                return;
            }
            try (var inputJar = new JarFile(artifact)) {
                try (var outputJarStream = new JarOutputStream(Files.newOutputStream(outputFile.toPath()))) {
                    inputJar.stream().forEach(entry -> {
                        try (var entryStream = inputJar.getInputStream(entry)) {
                            if (entry.getName().endsWith(".class")) {
                                ClassReader reader = new ClassReader(entryStream);
                                
                                String classType = entry.getName()
                                        .replace(".class", "");
                                List<String> toInject = injections.get(classType);
                                ClassWriter classWriter = new ClassWriter(Opcodes.ASM9);
                                if (toInject != null) {

                                    List<InterfaceInjection> toApply = toInject.stream()
                                            .map(toImpl -> InterfaceInjection.of(classType, toImpl))
                                            .toList();
                                    reader.accept(new InjectVisitor(Opcodes.ASM9, classWriter, toApply), 0);
                                } else reader.accept(classWriter, 0);

                                byte[] byteArray = classWriter.toByteArray();
                                JarEntry newEntry = new JarEntry(entry.getName());
                                newEntry.setTime(CONSTANT_TIME_FOR_ZIP_ENTRIES);
                                outputJarStream.putNextEntry(newEntry);
                                outputJarStream.write(byteArray);
                                outputJarStream.closeEntry();
                            } else {
                                JarEntry newEntry = new JarEntry(entry.getName());
                                newEntry.setTime(CONSTANT_TIME_FOR_ZIP_ENTRIES);
                                outputJarStream.putNextEntry(newEntry);
                                entryStream.transferTo(outputJarStream);
                                outputJarStream.closeEntry();
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }

                    });
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
        System.out.println();
    }


    //Code From:https://github.com/FabricMC/fabric-loom/blob/7c53939918cf63cdf4f176847088fd747c61e993/src/main/java/net/fabricmc/loom/configuration/ifaceinject/InterfaceInjectionProcessor.java
    private record InterfaceInjection(String target, String toImpl, @Nullable String generics) {
        public static InterfaceInjection of(String target, String toImpl) {

            String type = toImpl;
            String generics = null;

            if (toImpl.contains("<") && toImpl.contains(">")) {
                int start = toImpl.indexOf('<');
                int end = toImpl.lastIndexOf('>');
                type = toImpl.substring(0, start);

                // Extract the generics part and replace '.' with '/'
                String rawGenerics = toImpl.substring(start + 1, end).replace('.', '/');
                // Split the generics into individual components
                String[] genericComponents = rawGenerics.split(",\\s*(?![^<>]*>)");
                StringBuilder processedGenerics = new StringBuilder("<");

                for (int i = 0; i < genericComponents.length; i++) {
                    String component = genericComponents[i].trim();
                    // Handle nested generics
                    if (component.contains("<")) {
                        // Recursively process nested generics
                        component = processNestedGenerics(component);
                    } else {
                        // Handle simple types
                        component = "L" + component + ";";
                    }

                    processedGenerics.append(component);

//                    if (i < genericComponents.length - 1) {
//                        //processedGenerics.append(",");
//                    }
                }

                processedGenerics.append(">");
                generics = processedGenerics.toString();
                // First Generics Check, if there are generics, are they correctly written?
                SignatureReader reader = new SignatureReader("Ljava/lang/Object" + generics + ";");
                // Assuming CheckSignatureAdapter is a class that can handle the signature and reader is defined somewhere above
                CheckSignatureAdapter checker = new CheckSignatureAdapter(CheckSignatureAdapter.CLASS_SIGNATURE, null);
                reader.accept(checker);
            }
            return new InterfaceInjection(target, type, generics);
        }

        private static String processNestedGenerics(String component) {
            int start = component.indexOf('<');
            if (start == -1) {
                return "L" + component + ";";
            }
            int end = component.lastIndexOf('>');
            if (end == -1 || end <= start) {
                return "L" + component + ";";
            }
            String outerType = component.substring(0, start);
            System.out.println(outerType);
            String innerRawGenerics = component.substring(start + 1, end).replace('.', '/');
            // Split the inner generics into individual components
            String[] innerGenericComponents = innerRawGenerics.split(",\\s*(?![^<>]*>)");
            StringBuilder innerProcessedGenerics = new StringBuilder("<");

            for (int i = 0; i < innerGenericComponents.length; i++) {
                String innerComponent = innerGenericComponents[i].trim();

                // Handle nested generics recursively
                if (innerComponent.contains("<")) {
                    innerComponent = processNestedGenerics(innerComponent);
                } else {
                    // Handle simple types
                    innerComponent = "L" + innerComponent + ";";
                }

                innerProcessedGenerics.append(innerComponent);

//                if (i < innerGenericComponents.length - 1) {
//                    innerProcessedGenerics.append(",");
//                }
            }

            innerProcessedGenerics.append(">");
            return "L" + outerType + innerProcessedGenerics.toString() + ";";
        }


    }

    private static class InjectVisitor extends ClassVisitor {
        private static final int INTERFACE_ACCESS = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE;

        private final List<InterfaceInjection> interfaceInjections;
        private final Set<String> knownInnerClasses = new HashSet<>();

        InjectVisitor(int asmVersion, ClassWriter writer, List<InterfaceInjection> interfaceInjections) {
            super(asmVersion, writer);
            this.interfaceInjections = interfaceInjections;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            String[] baseInterfaces = interfaces.clone();
            Set<String> modifiedInterfaces = new LinkedHashSet<>(interfaces.length + interfaceInjections.size());
            Collections.addAll(modifiedInterfaces, interfaces);

            for (InterfaceInjection interfaceInjection : interfaceInjections) {
                modifiedInterfaces.add(interfaceInjection.toImpl());
            }

            // See JVMS: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-ClassSignature
            if (interfaceInjections.stream().anyMatch(injection -> injection.generics != null) && signature == null) {
                // Classes that are not using generics don't need signatures, so their signatures are null
                // If the class is not using generics but that an injected interface targeting the class is using them, we are creating the class signature
                StringBuilder baseSignatureBuilder = new StringBuilder("L" + superName + ";");

                for (String baseInterface : baseInterfaces) {
                    baseSignatureBuilder.append("L").append(baseInterface).append(";");
                }

                signature = baseSignatureBuilder.toString();
            }

            if (signature != null) {
                SignatureReader reader = new SignatureReader(signature);

                // Second Generics Check, if there are passed generics, are all of them present in the target class?
                GenericsChecker checker = new GenericsChecker(api, interfaceInjections);
                reader.accept(checker);
                checker.check();

                var resultingSignature = new StringBuilder(signature);

                for (InterfaceInjection interfaceInjection : interfaceInjections) {
                    String superinterfaceSignature;

                    if (interfaceInjection.generics() != null) {
                        superinterfaceSignature = "L" + interfaceInjection.toImpl() + interfaceInjection.generics() + ";";
                    } else {
                        superinterfaceSignature = "L" + interfaceInjection.toImpl() + ";";
                    }

                    if (resultingSignature.indexOf(superinterfaceSignature) == -1) {
                        resultingSignature.append(superinterfaceSignature);
                    }
                }

                signature = resultingSignature.toString();
            }

            super.visit(version, access, name, signature, superName, modifiedInterfaces.toArray(new String[0]));
        }

        @Override
        public void visitInnerClass(final String name, final String outerName, final String innerName, final int access) {
            this.knownInnerClasses.add(name);
            super.visitInnerClass(name, outerName, innerName, access);
        }

        @Override
        public void visitEnd() {
            // inject any necessary inner class entries
            // this may produce technically incorrect bytecode cuz we don't know the actual access flags for inner class entries,
            // but it's hopefully enough to quiet some IDE errors
            for (final InterfaceInjection itf : interfaceInjections) {
                if (this.knownInnerClasses.contains(itf.toImpl())) {
                    continue;
                }

                int simpleNameIdx = itf.toImpl().lastIndexOf('/');
                final String simpleName = simpleNameIdx == -1 ? itf.toImpl() : itf.toImpl().substring(simpleNameIdx + 1);
                int lastIdx = -1;
                int dollarIdx = -1;

                // Iterate through inner class entries starting from outermost to innermost
                while ((dollarIdx = simpleName.indexOf('$', dollarIdx + 1)) != -1) {
                    if (dollarIdx - lastIdx == 1) {
                        continue;
                    }

                    // Emit the inner class entry from this to the last one
                    if (lastIdx != -1) {
                        final String outerName = itf.toImpl().substring(0, simpleNameIdx + 1 + lastIdx);
                        final String innerName = simpleName.substring(lastIdx + 1, dollarIdx);
                        super.visitInnerClass(outerName + '$' + innerName, outerName, innerName, INTERFACE_ACCESS);
                    }

                    lastIdx = dollarIdx;
                }

                // If we have a trailer to append
                if (lastIdx != -1 && lastIdx != simpleName.length()) {
                    final String outerName = itf.toImpl().substring(0, simpleNameIdx + 1 + lastIdx);
                    final String innerName = simpleName.substring(lastIdx + 1);
                    super.visitInnerClass(outerName + '$' + innerName, outerName, innerName, INTERFACE_ACCESS);
                }
            }

            super.visitEnd();
        }
    }

    private static class GenericsChecker extends SignatureVisitor {
        private final List<String> typeParameters;

        private final List<InterfaceInjection> interfaceInjections;

        GenericsChecker(int asmVersion, List<InterfaceInjection> interfaceInjections) {
            super(asmVersion);
            this.typeParameters = new ArrayList<>();
            this.interfaceInjections = interfaceInjections;
        }

        @Override
        public void visitFormalTypeParameter(String name) {
            this.typeParameters.add(name);
            super.visitFormalTypeParameter(name);
        }

        // Ensures that injected interfaces only use collected type parameters from the target class
        public void check() {
            for (InterfaceInjection interfaceInjection : this.interfaceInjections) {
                if (interfaceInjection.generics() != null) {
                    SignatureReader reader = new SignatureReader("Ljava/lang/Object" + interfaceInjection.generics() + ";");
                    GenericsConfirm confirm = new GenericsConfirm(
                            api,
                            interfaceInjection.target(),
                            interfaceInjection.toImpl(),
                            this.typeParameters
                    );
                    reader.accept(confirm);
                }
            }
        }


    }

    private static class GenericsConfirm extends SignatureVisitor {
        private final String className;

        private final String interfaceName;

        private final List<String> acceptedTypeVariables;

        GenericsConfirm(int asmVersion, String className, String interfaceName, List<String> acceptedTypeVariables) {
            super(asmVersion);
            this.className = className;
            this.interfaceName = interfaceName;
            this.acceptedTypeVariables = acceptedTypeVariables;
        }

        @Override
        public void visitTypeVariable(String name) {
            if (!this.acceptedTypeVariables.contains(name)) {
                throw new IllegalStateException(
                        "Interface "
                                + this.interfaceName
                                + " attempted to use a type variable named "
                                + name
                                + " which is not present in the "
                                + this.className
                                + " class"
                );
            }

            super.visitTypeVariable(name);
        }
    }

}
