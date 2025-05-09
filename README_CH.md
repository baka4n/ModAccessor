# ModAccessor

一个简单的Gradle Plugin,用于解决编译期无法访问私有类型/字段/方法的问题。
**注意:你需要自己处理运行时的access transform**

## Usage
```groovy
modAccessor {
    createTransformConfiguration(configurations.compileOnly)
    accessTransformerFiles = project.files('src/main/resources/META-INF/accesstransformer.cfg')
}

dependencies {
    accessCompileOnly(("com.simibubi.create:create-${minecraft_version}:6.0.4-61:slim"))
}
```

## Credit

[fabric loom](https://github.com/FabricMC/fabric-loom) : 提供了最初版本的解决思路，帮我搞清楚了Artifact Transform如何工作

[moddev gradle](https://github.com/NeoForged/ModDevGradle) : 提供了LocalMaven的解决方案，并且源码转换基于loom的设计
