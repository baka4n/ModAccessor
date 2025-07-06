package dev.vfyjxf.gradle.accessor;

import net.neoforged.accesstransformer.api.AccessTransformerEngine;
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
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import static java.util.stream.StreamSupport.stream;

public abstract class AccessTransform implements TransformAction<AccessTransform.Parameters> {

    /**
     * {@link  org.gradle.api.internal.file.archive.ZipCopyAction#CONSTANT_TIME_FOR_ZIP_ENTRIES}
     */
    private static final long CONSTANT_TIME_FOR_ZIP_ENTRIES = new GregorianCalendar(1980, Calendar.FEBRUARY, 1, 0, 0, 0).getTimeInMillis();

    @InputArtifact
    @PathSensitive(PathSensitivity.NONE)
    public abstract Provider<FileSystemLocation> getInputArtifact();

    @Inject
    public AccessTransform() {}

    public interface Parameters extends TransformParameters {
        // Define any parameters you need for the transform
        @InputFiles
        @PathSensitive(PathSensitivity.NONE)
        ConfigurableFileCollection getAccessTransformerFiles();

    }

    @Override
    public void transform(@NotNull TransformOutputs outputs) {
        File artifact = getInputArtifact().get().getAsFile();
        if (!artifact.exists()) return;
        Parameters parameters = getParameters();
        List<Path> atFiles = stream(parameters.getAccessTransformerFiles().spliterator(), false)
                                     .map(File::toPath)
                                     .toList();
        var engine = AccessTransformerEngine.newEngine();
        for (Path atFile : atFiles) {
            try {
                engine.loadATFromPath(atFile);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        File outputFile = outputs.file(artifact.getName());
        try (var inputJar = new JarFile(artifact)) {
            try (var outputJarStream = new JarOutputStream(Files.newOutputStream(outputFile.toPath()))) {
                inputJar.stream().forEach(entry -> {
                    try (var entryStream = inputJar.getInputStream(entry)) {
                        if (entry.getName().endsWith(".class")) {
                            ClassReader reader = new ClassReader(entryStream);
                            ClassNode classNode = new ClassNode(Opcodes.ASM9);
                            reader.accept(classNode, 0);
                            final Type type = Type.getType('L' + classNode.name.replaceAll("\\.", "/") + ';');
                            engine.transform(classNode, type);
                            ClassWriter classWriter = new ClassWriter(Opcodes.ASM5);
                            classNode.accept(classWriter);
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
    }
}
