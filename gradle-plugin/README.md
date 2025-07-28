# Caupain Gradle plugin

![Gradle Plugin Portal Version](https://img.shields.io/gradle-plugin-portal/v/com.deezer.caupain)

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

You can launch the update check by running the following command in your terminal:
```bash
./gradlew checkDependencyUpdates --no-configuration-cache
```

### Snapshots

![Maven metadata URL](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fcentral.sonatype.com%2Frepository%2Fmaven-snapshots%2Fcom%2Fdeezer%2Fcaupain%2Fcom.deezer.caupain.gradle.plugin%2Fmaven-metadata.xml&versionSuffix=.0-SNAPSHOT)

You can use the latest snapshot version by using the version in the badge above and adding the following
repository to your `settings.gradle.kts` file:
```kotlin
pluginManagement {
    repositories {
        maven("https://central.sonatype.com/repository/maven-snapshots/") {
            mavenContent {
                snapshotsOnly()
            }
        }
        // ... your other repositories
    }
}
```

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
// in the group are excluded. Furthermore, if name is not specified, then group is interpreted as a glob.
excludeLibrary(group = "com.example")
excludeLibrary(group = "com.example.**")
excludeLibrary(group = "com.example.*.sub")
excludeLibrary(group = "com.example", name = "example")
excludeLibraries(
        LibraryExclusion(group = "com.example"),
        LibraryExclusion(group = "com.example", name = "example")
)
// You can exclude plugins by their id
excludePluginIds("excluded.plugin.id")
```

You can also exclude dependencies directly in the TOML file by adding an inline comment `#ignoreUpdates`
on the same line as a version reference or dependency/plugin definition, like so:
```toml
[versions]
my-excluded-version-ref = "1.0.0" #ignoreUpdates
my-regular-ref = "1.0.0"
[libraries]
my-excluded-lib = { module = "com.example:example", version.ref = "my-excluded-version-ref" } #ignoreUpdates
my-regular-lib = { module = "com.example:example", version.ref = "my-regular-ref" }
[plugins]
my-excluded-plugin = { id = "com.example.plugin", version.ref = "my-excluded-version-ref" } #ignoreUpdates
my-regular-plugin = { id = "com.example.plugin", version.ref = "my-regular-ref" }
```

### Repositories

By default, the Maven repositories that are used are the ones defined in the the `pluginManagement`
and `dependencyResolutionManagement` of your `settings.gradle.kts` file, plus the ones defined in the 
`repositories` or `buildScript.repositories` of the projects. If you want to override these,
you can use the following syntax:
```kotlin
repositories {
    // Defines the repositories for the dependencies
    libraries {
        // Default repositories "google", "mavenCentral" and "gradlePluginPortal" are provided as shortcuts
        repository(DefaultRepositories.google)
        // You can also specify a custom repository
        repository("https://www.example.com/maven2")
        // With optional password credentials
        repository("https://www.example.com/maven2", "user", "password")
        // Or with header credentials
        repository("https://www.example.com/maven2") {
            headerCredentials {
                name = "headerName"
                value = "headerValue"
            }
        }
    }
    // Defines the repositories for the plugins
    plugins {
        repository("https://www.example.com/maven2")
        repository("https://www.example.com/maven2", "user", "password")
    }
}
```

Repositories are queried in the order they are specified.

#### Repository component filtering

By default, Caupain will search for dependencies in all repositories, going through them in order until
the dependency is found. If you want to specify what kind of dependencies are in each repository, you
can set up component filtering like so:
```kotlin
repositories {
    libraries {
        // You can add filtering on default repositories
        repository(DefaultRepositories.google) {
            // If only exclusions are defined, then all components except those excluded will be searched
            // for in the repository. If only inclusions are defined, then only those components will be searched
            // for in the repository. If both are defined, then only the components that are included and not excluded
            // will be searched for in the repository.
            include(group = "com.example", name = "example") // You can specify a group and name
            include(group = "com.example") // You can also only specify a group...
            include(group = "com.example.**") // ...or use globs
            exclude(group = "com.example")
        }
        // This can also be done for custom repositories
        repository("https://www.example.com/maven2") {
            //include(...)
            //exclude(...)
        }
    }
    // The same can be done for plugins
    plugins {
        //...
    }
}
```

### Policies

Policies are used to filter available updates. You can either use a predefined policy or create your own.

#### Predefined policies

There's only one predefined policy at the moment, which is based on stability level.
This policy will only select an update if its stability is greater than or equal to the stability of 
the current version.

This means that if you're using a beta version, alpha versions won't be suggested for you, and so on.

#### Configuration

They are defined via task configuration like so:
```kotlin
tasks.withType<DependenciesUpdateTask> {
    // You can use one of the predefined policies
    selectIf(StabilityLevelPolicy)
    // Or define your own. In the "selectIf" block, "this" will have properties "dependency", "currentVersion" and
    // "updatedVersion" which are the dependency, current and updated versions of the dependency.
    selectIf {
        updatedVersion.text == "1.0.0"
    }
}
```

### Outputs

By default, the plugins outputs both a console result and an HTML file in the `build/reports` directory.
This can be customized like so:
```kotlin
outputs {
    // Console output. Enabled by default
    console {
        enabled = true
    }
    // HTML output. Enabled by default. Default output file is "build/reports/dependency-updates.html"
    html {
        enabled = true
        outputFile = file("path/to/output.html")
    }
    // Markdown output. Disabled by default. Default output file is "build/reports/dependency-updates.md"
    markdown {
        enabled = true
        outputFile = file("path/to/output.md")
    }
    // JSON output. Disabled by default. Default output file is "build/reports/dependency-updates.json"
    json {
        enabled = true
        outputFile = file("path/to/output.json")
    }
}
```

#### Versions references block
By default, the plugin will show all dependencies atomically, but will not provide a summary block 
based on the content of the `versions` block in the version catalog. If you heavily use this block and
want a quick summary to easily update what's needed, you can enable the `showVersionsReferences` switch
in the configuration block like so:
```kotlin
caupain {
    // Other configuration...
    showVersionsReferences = true
}
```

#### Custom formatter
If needed, you can also create a custom formatter by providing your custom implementation in the task
configuration like so:
```kotlin
tasks.withType<DependenciesUpdateTask> {
    customFormatter { result ->
        // output whatever you want here
    }
}
```

### Other options

```kotlin
// Version catalog path. Default is "gradle/libs.versions.toml".
versionCatalogFile = file("path/to/libs.versions.toml")
// You can also define multiple version catalogs. Warning: defining this will override the previous single path
versionCatalogFiles.from("path/to/libs.versions.toml", "path/to/other/libs.versions.toml")
// Whether or not to store HTTP cache for the Maven requests
useCache = true
// By default, Caupain only checks updates for static versions in the version catalog (versions like 
// 1.0.0 or 1.0.0-SNAPSHOT).
// The rationale behind this is that dynamic versions or rich versions indicate that the version range
// is already specified, and that an update isn't needed. If this value is passed to false, Caupain 
// will try to find the latest version for all dependencies, even if they are dynamic or rich versions.
// This is where the result may not be exactly what you want, because Caupain will not use Gradle to
// try to resolve the exact dependency that is used.
// Default is true.
onlyCheckStaticVersions = true
// The stability level to use for Gradle version checks. Default is STABLE.
gradleStabilityLevel = GradleStabilityLevel.STABLE
// Whether or not to show a section in the report about the ignored available updates.
checkIgnored = true
```

### Minimum Gradle version

The minimum Gradle versions for the plugin depending on the version of the plugin are as follows:

| Plugin version | Minimum Gradle version |
|----------------|------------------------|
| 1.2.0          | 8.8                    |