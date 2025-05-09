package dev.vfyjxf.gradle.accessor;

import dev.vfyjxf.gradle.accessor.dependency.ModDependencyHandler;
import net.neoforged.jst.accesstransformers.AccessTransformersTransformer;
import net.neoforged.jst.api.SourceTransformer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ModAccessorPlugin implements Plugin<Project> {

    @Override
    public void apply(@NotNull Project project) {
        var extension = project.getExtensions().create(
                "modAccessor",
                ModAccessTransformExtension.class,
                project
        );
        var configuration = project.getConfigurations().create("transformAccess", config -> {
            config.setDescription("Transform mod dependency access");
            config.setCanBeResolved(true);
            config.setCanBeConsumed(true);
            config.setTransitive(false);
        });
        project.afterEvaluate(p -> {
            if (p.getState().getFailure() == null) {
                List<SourceTransformer> sourceTransformers = new ArrayList<>();
                var accessTransformer = new AccessTransformersTransformer();
                accessTransformer.atFiles = extension.getAccessTransformerFiles()
                                                    .getFiles()
                                                    .stream()
                                                    .map(File::toPath)
                                                    .toList();
                sourceTransformers.add(accessTransformer);
                ModTransformer transformer = new ModTransformer(extension.getAccessTransformerFiles(), sourceTransformers);
                ModDependencyHandler.run(transformer, project, extension.transformToSourceConfigurations());
            }
        });
    }


}
