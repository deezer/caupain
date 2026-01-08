@file:Suppress("UnstableApiUsage")

rootProject.name = "caupain"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(":core")
include(":cli")
include(":gradle-plugin")
include(":sink-test")