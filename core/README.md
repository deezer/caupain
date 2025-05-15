# Caupain core library

![Maven Central Version](https://img.shields.io/maven-central/v/com.deezer.caupain/core)

This is the core library of Caupain, used by the [Gradle plugin](../gradle-plugin/README.md) and the 
[CLI tool](../cli/README.md). If you want to use the dependency resolution engine of Caupain, you can use this library

## How to use

Add the following lines to your `libs.versions.toml` file:

```toml
[versions]
caupain = "1.0.1"
...your other versions

[libraries]
caupain-core = { module = "com.deezer.caupain:core", version.ref = "caupain" }
... The rest of your file
```

You can then use the dependency in your project with the following line in the `dependencies` block:
```kotlin
implementation(libs.caupain.core)
```
