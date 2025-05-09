package dev.vfyjxf.gradle.accessor;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.jetbrains.annotations.NotNull;

public class ModAccessorPlugin implements Plugin<Project> {
    @Override
    public void apply(@NotNull Project project) {
        var extension = project.getExtensions().create(
                "modAccessor",
                ModAccessTransformExtension.class,
                project
        );
        project.getDependencies().getArtifactTypes().named(ArtifactTypeDefinition.JAR_TYPE, type -> {
            type.getAttributes().attribute(ModAccessTransformExtension.TRANSFORM_ACCESS, false);
        });
        project.getDependencies().registerTransform(
                AccessTransform.class,
                parameters -> {
                    parameters.parameters(p -> {
                        p.getAccessTransformerFiles().from(extension.getAccessTransformerFiles());
                    });
                    parameters.getFrom().attribute(
                            ModAccessTransformExtension.TRANSFORM_ACCESS,
                            false
                    ).attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
                    parameters.getTo().attribute(
                            ModAccessTransformExtension.TRANSFORM_ACCESS,
                            true
                    ).attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
                }
        );
    }


}
