package dev.vfyjxf.gradle.accessor;

import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.ConfigurableFileCollection;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

public abstract class ModAccessTransformExtension {

    private final Project project;

    private final Map<Configuration, Configuration> transformToSource = new HashMap<>();

    public abstract ConfigurableFileCollection getAccessTransformerFiles();

    @Inject
    public ModAccessTransformExtension(Project project) {
        this.project = project;
    }

    public Configuration createTransformConfiguration(Configuration parent) {
        var transformer = project.getConfigurations().create("access" + StringUtils.capitalize(parent.getName()), spec -> {
            spec.setDescription("Configuration for dependencies of " + parent.getName() + " that needs to be transformed");
            // Don't get transitive deps of already remapped mods
            spec.setTransitive(false);
            final Usage usage = project.getObjects().named(Usage.class, Usage.JAVA_API);
            spec.attributes(attributes -> attributes.attribute(Usage.USAGE_ATTRIBUTE, usage));
        });
        transformToSource.put(transformer, parent);
        return transformer;
    }

    public Map<Configuration, Configuration> transformToSourceConfigurations() {
        return transformToSource;
    }
}