plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinx.serialization) apply false
    alias(libs.plugins.jetbrains.kotlin.jvm) apply false
}

subprojects {
    group = "com.deezer.dependencies"
    version = "0.0.1-SNAPSHOT"
}