package dev.vfyjxf.gradle.accessor;

import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.file.ConfigurableFileCollection;

import javax.inject.Inject;

public abstract class ModAccessTransformExtension {

    static Attribute<Boolean> TRANSFORM_ACCESS = Attribute.of("accessor_plugin_transform_access", Boolean.class);

    private final Project project;

    public abstract ConfigurableFileCollection getAccessTransformerFiles();

    @Inject
    public ModAccessTransformExtension(Project project) {
        this.project = project;
    }

    public Configuration createTransformConfiguration(Configuration parent) {
        var transformAccess = project.getConfigurations().create("access" + StringUtils.capitalize(parent.getName()), spec -> {
            spec.setDescription("Configuration for dependencies of " + parent.getName() + " that needs to be remapped");
            spec.setCanBeConsumed(false);
            spec.setCanBeResolved(false);
            spec.setTransitive(false);

            //code based MDG legacy
            spec.withDependencies(dependencies -> dependencies.forEach(dep -> {
                if (dep instanceof ExternalModuleDependency externalModuleDependency) {
                    project.getDependencies().constraints(constraints -> {
                        constraints.add(parent.getName(), externalModuleDependency.getGroup() + ":" + externalModuleDependency.getName() + ":" + externalModuleDependency.getVersion(), c -> {
                            c.attributes(a -> a.attribute(TRANSFORM_ACCESS, true));
                        });
                    });
                    externalModuleDependency.setTransitive(false);
                } else if (dep instanceof FileCollectionDependency fileCollectionDependency) {
                    project.getDependencies().constraints(constraints -> {
                        constraints.add(parent.getName(), fileCollectionDependency.getFiles(), c -> {
                            c.attributes(a -> a.attribute(TRANSFORM_ACCESS, true));
                        });
                    });
                } else if (dep instanceof ProjectDependency projectDependency) {
                    project.getDependencies().constraints(constraints -> {
                        constraints.add(parent.getName(), projectDependency.getDependencyProject(), c -> {
                            c.attributes(a -> a.attribute(TRANSFORM_ACCESS, true));
                        });
                    });
                    projectDependency.setTransitive(false);
                }
            }));
        });
        parent.extendsFrom(transformAccess);
        transformAccess.getAttributes().attribute(TRANSFORM_ACCESS, false);
        return transformAccess;
    }
}