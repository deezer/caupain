import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension

plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinx.serialization) apply false
    alias(libs.plugins.jetbrains.kotlin.jvm) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.changelog)
    alias(libs.plugins.fix.kmp.metadata)
}

val currentVersion = "0.1.0"

val isSnapshot = project.findProperty("isSnapshot")?.toString().toBoolean()
val isCI = System.getenv("CI").toBoolean()
version = buildString {
    append(currentVersion)
    if (isSnapshot || !isCI) append(".0-SNAPSHOT")
}

val ignoredForChecks = listOf(
    projects.samplePluginPolicy.name
)

subprojects {
    group = "com.deezer.caupain"
    version = rootProject.version

    if (name !in ignoredForChecks) {
        apply(plugin = "io.gitlab.arturbosch.detekt")

        extensions.configure<DetektExtension> {
            config.setFrom(rootProject.file("code-quality/detekt.yml"))
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
}