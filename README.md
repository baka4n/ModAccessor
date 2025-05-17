# ModAccessor

A simple gradle plugin to solve the problem of accessing private fields and methods during compile time.

**You should transform the classes in runtime by yourself.**


## Usage

[![ModAccessor](https://img.shields.io/maven-metadata/v/https/plugins.gradle.org/m2/dev/vfyjxf/modaccessor/dev.vfyjxf.modaccessor.gradle.plugin/maven-metadata.xml.svg?label=Gradle%20Plugin%20Portal)](https://plugins.gradle.org/plugin/dev.vfyjxf.modaccessor)

```groovy

plugins{
    id("dev.vfyjxf.modaccessor") version "1.0"
}

modAccessor {
    createTransformConfiguration(configurations.compileOnly)
    accessTransformerFiles = project.files('src/main/resources/META-INF/accesstransformer.cfg')
}

dependencies {
    accessCompileOnly(("com.simibubi.create:create-${minecraft_version}:6.0.4-61:slim"))
}
```


## Credit

[fabric loom](https://github.com/FabricMC/fabric-loom) : The provided LocalMaven solution spares me the hassle of battling with Gradle Artifact Transforms

[moddev gradle](https://github.com/NeoForged/ModDevGradle) : It helped me understand artifact transforms, as the initial version used them to handle deobfuscation of the code.
