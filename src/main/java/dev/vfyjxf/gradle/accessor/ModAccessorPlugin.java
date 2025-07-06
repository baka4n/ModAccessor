package dev.vfyjxf.gradle.accessor;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class ModAccessorPlugin implements Plugin<Project> {
    @Override
    public void apply(@NotNull Project project) {
        var extension = project.getExtensions().create(
                "modAccessor",
                ModAccessorExtension.class,
                project
        );
        project.getDependencies().getArtifactTypes().named(
                ArtifactTypeDefinition.JAR_TYPE, type -> {
                    type.getAttributes().attribute(ModAccessorExtension.TRANSFORM_ACCESS, false);
                }
        );
        project.getDependencies().registerTransform(
                AccessTransform.class,
                parameters -> {
                    parameters.parameters(p -> {

                        p.getAccessTransformerFiles().from(extension.getAccessTransformerFiles());
                    });
                    parameters.getFrom().attribute(
                            ModAccessorExtension.TRANSFORM_ACCESS,
                            false
                    ).attribute(
                            ModAccessorExtension.TRANSFORM_INTERFACE_INJECT,
                            false
                    ).attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);

                    parameters.getTo().attribute(
                            ModAccessorExtension.TRANSFORM_ACCESS,
                            true
                    ).attribute(
                            ModAccessorExtension.TRANSFORM_INTERFACE_INJECT,
                            false
                    ).attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
                }
        );

        project.getDependencies().registerTransform(
                InterfaceInjectionTransform.class,
                parameters -> {
                    parameters.parameters(p -> {
                        p.getInterfaceInjectionFiles().from(extension.getInterfaceInjectionFiles());
                    });

                    parameters.getFrom().attribute(
                            ModAccessorExtension.TRANSFORM_ACCESS,
                            true
                    ).attribute(
                            ModAccessorExtension.TRANSFORM_INTERFACE_INJECT,
                            false
                    ).attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
                    parameters.getTo().attribute(
                            ModAccessorExtension.TRANSFORM_ACCESS,
                            true
                    ).attribute(
                            ModAccessorExtension.TRANSFORM_INTERFACE_INJECT,
                            true
                    ).attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
                }
        );
        project.afterEvaluate(p -> {
            var atFiles = extension.getAccessTransformerFiles();
            if (atFiles.isEmpty() || atFiles.getFiles().stream().noneMatch(File::exists)) {
                p.getLogger().error("[ModAccessor]: No access transformer files found. Please add some.");
            }
        });
    }


}
