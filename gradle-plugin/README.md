# Caupain Gradle plugin

This is the Gradle plugin version of the tool.

## Installation

Add the following line to your `libs.versions.toml` file in the `[plugins]` section:
```toml
caupain = "com.deezer.caupain:latestVersion"
```
with `latestVersion` being the version you want to use of the plugin (see [releases](https://github.com/bishiboosh/caupain/releases)).

Then, apply the plugin in your root `build.gradle.kts` file:
```kotlin
plugins {
    alias(libs.plugins.caupain)
}
```

This will add a task named `checkDependencyUpdates` to your root project, which when launched will start 
the update check.

## Configuration

Most of the configuration is done via the `caupain` block in your root `build.gradle.kts` file, like so:
```kotlin
caupain {
    // Your configuration here
}
```

### Exclusions

You can exclude dependencies from the update check by a variety of means:
```kotlin
// You can exclude by key. The key matches the name used in the version catalog.
excludeKeys("excluded1", "excluded2")
// You can also exclude by group and optionally by name. If name is not specified, all dependencies 
// in the group are excluded.
excludeLibrary(group = "com.example")
excludeLibrary(group = "com.example", name = "example")
excludeLibraries(
        LibraryExclusion(group = "com.example"),
        LibraryExclusion(group = "com.example", name = "example")
)
// You can exclude plugins by their id
excludePluginIds("excluded.plugin.id")
```

### Repositories

By default, the Maven repositories that are used are the ones defined in the the `pluginManagement`
and `dependencyResolutionManagement` of your `settings.gradle.kts` file. If you want to override these,
you can use the following syntax:
```kotlin
repositories {
    // Defines the repositories for the dependencies
    libraries {
        // Default repositories "google", "mavenCentral" and "gradlePluginPortal" are provided as shortcuts
        repository(DefaultRepositories.google)
        // You can also specify a custom repository
        repository("https://www.example.com/maven2")
        // With optional credentials
        repository("https://www.example.com/maven2", "user", "password")
    }
    // Defines the repositories for the plugins
    plugins {
        repository("https://www.example.com/maven2")
        repository("https://www.example.com/maven2", "user", "password")
    }
}
```

Repositories are queried in the order they are specified.

### Policies

Policies are used to filter available updates. You can either use a predefined policy or create your own.

#### Predefined policies

There's only one predefined policy at the moment, which is based on versions level (like in AndroidX).
This policy will only select an update if its stability is greater than or equal to the stability of 
the current version.

This means that if you're using a beta version, alpha versions won't be suggested for you, and so on.

#### Configuration

They are defined via task configuration like so:
```kotlin
tasks.withType<DependenciesUpdateTask> {
    // You can use one of the predefined policies
    selectIf(AndroidXVersionLevelPolicy)
    // Or define your own. In the "selectIf" block, "this" will have properties "currentVersion" and
    // "updatedVersion" which are the current and updated versions of the dependency.
    selectIf {
        updatedVersion.text == "1.0.0"
    }
}
```

### Other options

```kotlin
// Version catalog path. Default is "gradle/libs.versions.toml".
versionCatalogFile = file("path/to/libs.versions.toml")
// Output path. Default is build/reports/dependencies-update.md
outputFile = File(rootProject.projectDir, "path/to/output/file.html")
// Whether or not to ouput update result to console
outputToConsole = true
// Whether or not to ouput update result to HTML file
outputToFile = true
// Whether or not to store HTTP cache for the Maven requests
useCache = true
// By default, Caupain only checks updates for static versions in the version catalog (versions like 
// 1.0.0 or 1.0.0-SNAPSHOT).
// The rationale behind this is that dynamic versions or rich versions indicate that the version range
// is already specified, and that an update isn't needed. If this value is passed to false, Caupain 
// will try to find the latest version for all dependencies, even if they are dynamic or rich versions.
// Default is true.
onlyCheckStaticVersions = true
```