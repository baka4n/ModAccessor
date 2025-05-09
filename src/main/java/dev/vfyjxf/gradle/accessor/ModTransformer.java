package dev.vfyjxf.gradle.accessor;

import dev.vfyjxf.gradle.accessor.dependency.ModDependency;
import net.neoforged.accesstransformer.api.AccessTransformerEngine;
import net.neoforged.jst.api.Logger;
import net.neoforged.jst.api.SourceTransformer;
import net.neoforged.jst.cli.PathType;
import net.neoforged.jst.cli.io.FileSinks;
import net.neoforged.jst.cli.io.FileSources;
import net.neoforged.problems.Problem;
import net.neoforged.problems.ProblemReporter;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.logging.LogLevel;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public final class ModTransformer {
    /**
     * {@link  org.gradle.api.internal.file.archive.ZipCopyAction#CONSTANT_TIME_FOR_ZIP_ENTRIES}
     */
    private static final long CONSTANT_TIME_FOR_ZIP_ENTRIES = new GregorianCalendar(1980, Calendar.FEBRUARY, 1, 0, 0, 0).getTimeInMillis();
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(ModTransformer.class);

    private final AccessTransformerEngine engine;
    private final List<SourceTransformer> sourceTransformers;

    ModTransformer(ConfigurableFileCollection atFiles, List<SourceTransformer> sourceTransformers) {
        this.sourceTransformers = sourceTransformers;
        this.engine = AccessTransformerEngine.newEngine();
        atFiles.forEach(file -> {
            try {
                engine.loadATFromPath(file.toPath());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }


    public void transformMod(ModDependency dependency) {
        try {
            Path workingFile = dependency.getWorkingFile(null);
            Files.deleteIfExists(workingFile);
            try (var inputJar = new JarFile(dependency.artifact().path().toFile())) {
                if (workingFile.getParent() != null) {
                    Files.createDirectories(workingFile.getParent());
                }
                try (var outputJarStream = new JarOutputStream(Files.newOutputStream(workingFile))) {
                    inputJar.stream().forEach(entry -> {
                        try (var entryStream = inputJar.getInputStream(entry)) {
                            if (entry.getName().endsWith(".class")) {
                                ClassReader reader = new ClassReader(entryStream);
                                ClassNode classNode = new ClassNode(Opcodes.ASM9);
                                reader.accept(
                                        classNode, 0
                                );
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
            }
            dependency.copyToCache(workingFile, null);
            dependency.applyToProject();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    @SuppressWarnings("ConstantConditions")
    public void transformModSource(ModDependency dependency) {
        Path sources = dependency.artifact().sources();
        Path workingFile = dependency.getWorkingFile("sources");
        var logger = new Logger(null, System.err);
        ProblemReporter problemReporter = new GradleLoggerProblemReporter(dependency.project());
        try (var source = FileSources.create(sources, PathType.AUTO);
             var processor = new SourceFileProcessor(logger, problemReporter)) {

            processor.setMaxQueueDepth(100);

            var orderedTransformers = new ArrayList<>(sourceTransformers);

            try (var sink = FileSinks.create(workingFile, PathType.AUTO, source)) {
                if (!processor.process(source, sink, orderedTransformers)) {
                    logger.error("Transformation failed");
                }
            }
            dependency.copyToCache(workingFile, "sources");
            dependency.applyToProject();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static class GradleLoggerProblemReporter implements ProblemReporter {
        private final Project project;

        public GradleLoggerProblemReporter(Project project) {
            this.project = project;
        }

        @Override
        public void report(Problem problem) {
            project.getLogger().log(LogLevel.ERROR, problem.toString());
        }
    }


}
