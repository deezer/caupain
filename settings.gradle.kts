@file:Suppress("UnstableApiUsage")

rootProject.name = "DependencyUpdate"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("pluginLibs") {
            from(files("gradle/pluginLibs.versions.toml"))
        }
    }
    repositories {
        mavenCentral()
    }
}

include(":core")
include(":core-compat")
include(":cli")
include(":gradle-plugin")