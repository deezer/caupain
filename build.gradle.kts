import io.gitlab.arturbosch.detekt.extensions.DetektExtension

plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinx.serialization) apply false
    alias(libs.plugins.jetbrains.kotlin.jvm) apply false
    alias(libs.plugins.detekt)
}

subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")

    group = "com.deezer.caupain"
    version = "0.0.1-SNAPSHOT"

    extensions.configure<DetektExtension> {
        config.setFrom(rootProject.file("code-quality/detekt.yml"))
    }
}

tasks.register("checkUpdates") {
    group = "verification"
    description = "Check for dependency updates"
    dependsOn("cli:runJvm")
}