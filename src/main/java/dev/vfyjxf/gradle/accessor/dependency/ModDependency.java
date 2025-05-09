package dev.vfyjxf.gradle.accessor.dependency;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

public record ModDependency(Project project, Configuration sourceConfiguration, LocalMavenHelper maven,
                            ArtifactRef artifact) {
    public ModDependency(Project project, Configuration sourceConfiguration, ArtifactRef artifact) {
        this(project, sourceConfiguration, createMaven(project, artifact), artifact);
    }

    private static LocalMavenHelper createMaven(Project project, ArtifactRef artifact) {
        return new LocalMavenHelper(
                "mod_accessor." + artifact.group(),
                artifact.name(),
                artifact.version(),
                artifact.classifier(),
                LocalMavenHelper.transformedModCache(project)
        );
    }


    public boolean isCacheInvalid(@Nullable String variant) {
        return !maven.exists(variant);
    }

    public void copyToCache(Path path, @Nullable String variant) throws IOException {
        maven.copyToMaven(path, variant);
    }

    public void applyToProject() {
        project.getDependencies().add(sourceConfiguration.getName(), maven.getNotation());
    }

    public Path getWorkingFile(@Nullable String classifier) {
        final String fileName = classifier == null ? String.format("%s-%s-%s.jar", maven.group(), artifact.name(), artifact.version())
                                        : String.format("%s-%s-%s-%s.jar", maven.group(), artifact.name(), artifact.version(), classifier);

        return LocalMavenHelper.buildCachePath(project).resolve("transformed_working").resolve(fileName);
    }
}
