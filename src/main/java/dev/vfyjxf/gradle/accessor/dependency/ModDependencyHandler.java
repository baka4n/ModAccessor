package dev.vfyjxf.gradle.accessor.dependency;

import dev.vfyjxf.gradle.accessor.ModTransformer;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.query.ArtifactResolutionQuery;
import org.gradle.api.artifacts.result.ArtifactResult;
import org.gradle.api.artifacts.result.ComponentArtifactsResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.FileCollection;
import org.gradle.jvm.JvmLibrary;
import org.gradle.language.base.artifact.SourcesArtifact;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class ModDependencyHandler {

    public static final String MISSING_GROUP = "unspecified";

    public static void run(ModTransformer transformer, Project project, Map<Configuration, Configuration> transformToSource) {
        project.getRepositories().maven(maven -> {
            maven.setName("Mod Accessor Local Maven");
            maven.setUrl(LocalMavenHelper.transformedModCache(project));
        });
        transformToSource.forEach((transformed, source) -> {
            var artifacts = resolveArtifacts(project, transformed);
            List<ModDependency> dependencies = artifacts.stream()
                                                       .map(artifact -> new ModDependency(project, source, artifact))
                                                       .toList();
            for (var dependency : dependencies) {
                if (!dependency.isCacheInvalid(null)) continue;
                transformer.transformMod(dependency);
            }
            for (ModDependency dependency : dependencies) {
                if (!dependency.isCacheInvalid("sources")) continue;
                if (dependency.artifact().sources() != null) {
                    transformer.transformModSource(dependency);
                }
            }
        });

    }

    private static List<ArtifactRef> resolveArtifacts(Project project, Configuration configuration) {
        final List<ArtifactRef> artifacts = new ArrayList<>();

        final Set<ResolvedArtifact> resolvedArtifacts = configuration.getResolvedConfiguration().getResolvedArtifacts();
        downloadAllSources(project, resolvedArtifacts);

        for (ResolvedArtifact artifact : resolvedArtifacts) {
            final Path sources = findSources(project, artifact);
            artifacts.add(new ArtifactRef.ResolvedArtifactRef(artifact, sources));
        }

        // FileCollectionDependency (files/fileTree) doesn't resolve properly,
        // so we have to "resolve" it on our own. The naming is "abc.jar" => "unspecified:abc:unspecified".
        for (FileCollectionDependency dependency : configuration.getAllDependencies().withType(FileCollectionDependency.class)) {
            final String group = replaceIfNullOrEmpty(dependency.getGroup(), () -> MISSING_GROUP);
            final FileCollection files = dependency.getFiles();

            for (File artifact : files) {
                final String name = getNameWithoutExtension(artifact.toPath());
                final String version = replaceIfNullOrEmpty(dependency.getVersion(), () -> Checksum.truncatedSha256(artifact));
                artifacts.add(new ArtifactRef.FileArtifactRef(artifact.toPath(), group, name, version));
            }
        }

        return artifacts;
    }

    private static void downloadAllSources(Project project, Set<ResolvedArtifact> resolvedArtifacts) {

        final DependencyHandler dependencies = project.getDependencies();

        List<ComponentIdentifier> componentIdentifiers = resolvedArtifacts.stream()
                                                                 .map(ResolvedArtifact::getId)
                                                                 .map(ComponentArtifactIdentifier::getComponentIdentifier)
                                                                 .toList();

        //noinspection unchecked
        ArtifactResolutionQuery query = dependencies.createArtifactResolutionQuery()
                                                .forComponents(componentIdentifiers)
                                                .withArtifacts(JvmLibrary.class, SourcesArtifact.class);

        // Run a single query for all of the artifacts, this will allow them to be resolved in parallel before they are queried individually
        query.execute();
    }

    @Nullable
    public static Path findSources(Project project, ResolvedArtifact artifact) {

        final DependencyHandler dependencies = project.getDependencies();


        @SuppressWarnings("unchecked")
        ArtifactResolutionQuery query = dependencies.createArtifactResolutionQuery()
                                                .forComponents(artifact.getId().getComponentIdentifier())
                                                .withArtifacts(JvmLibrary.class, SourcesArtifact.class);

        for (ComponentArtifactsResult result : query.execute().getResolvedComponents()) {
            for (ArtifactResult srcArtifact : result.getArtifacts(SourcesArtifact.class)) {
                if (srcArtifact instanceof ResolvedArtifactResult) {
                    return ((ResolvedArtifactResult) srcArtifact).getFile().toPath();
                }
            }
        }

        return null;
    }

    public static String replaceIfNullOrEmpty(@Nullable String s, Supplier<String> fallback) {
        return s == null || s.isEmpty() ? fallback.get() : s;
    }

    private static String getNameWithoutExtension(Path file) {
        final String fileName = file.getFileName().toString();
        final int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
    }

}
