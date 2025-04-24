import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension

plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinx.serialization) apply false
    alias(libs.plugins.jetbrains.kotlin.jvm) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.changelog)
}

version = "0.0.1-SNAPSHOT"

subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")

    group = "com.deezer.caupain"
    version = rootProject.version

    extensions.configure<DetektExtension> {
        config.setFrom(rootProject.file("code-quality/detekt.yml"))
    }
    tasks.withType<Detekt> {
        reports.sarif.required.set(true)
    }
    val detektAll = tasks.register("detektAll") {
        group = "verification"
        description = "Run detekt analysis for all targets"
        dependsOn(
            tasks
                .withType<Detekt>()
                .matching { !it.name.contains("test", ignoreCase = true) }
        )
    }
    afterEvaluate {
        tasks.named("check") {
            dependsOn(detektAll)
        }
    }
}

tasks.register("checkUpdates") {
    group = "verification"
    description = "Check for dependency updates"
    dependsOn("cli:runJvm")
}