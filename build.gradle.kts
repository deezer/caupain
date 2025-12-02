import com.deezer.caupain.tasks.FixKMPMetadata
import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import dev.detekt.gradle.report.ReportMergeTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompileTool

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlinx.serialization) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.changelog)
    alias(libs.plugins.dependency.guard) apply false
    alias(libs.plugins.vanniktech.maven.publish) apply false
}

val currentVersion = "1.7.1"

val isSnapshot = project.findProperty("isSnapshot")?.toString().toBoolean()
val isRelease = project.findProperty("isRelease")?.toString().toBoolean()
val versionSuffix = project.findProperty("versionSuffix")?.toString()
val isCI = System.getenv("CI").toBoolean()
version = buildString {
    append(currentVersion)
    if (!isRelease && (isSnapshot || !isCI)) append(".0")
    if (!versionSuffix.isNullOrBlank()) append("-$versionSuffix")
    if (!isRelease && (isSnapshot || !isCI)) append("-SNAPSHOT")
}

changelog {
    version.set(currentVersion)
    outputFile.set(layout.buildDirectory.file("release-notes.md"))
}

val mergeDetektReports = tasks.register<ReportMergeTask>("mergeDetektReports") {
    output = layout.buildDirectory.file("reports/detekt/merged.sarif")
}

subprojects {
    group = "com.deezer.caupain"
    version = rootProject.version

    if (name != "core-compat") {
        apply(plugin = "dev.detekt")

        extensions.configure<DetektExtension> {
            config.from(rootProject.layout.projectDirectory.file("code-quality/detekt.yml"))
            basePath.set(rootDir.absoluteFile)
            buildUponDefaultConfig = true
        }
        val detektAll = tasks.register("detektAll") {
            group = "verification"
            description = "Run detekt analysis for all targets"
        }
        tasks.withType<Detekt> {
            if (!name.contains("test", ignoreCase = true)) {
                detektAll {
                    dependsOn(this@withType)
                }
                mergeDetektReports {
                    input.from(this@withType.reports.sarif.outputLocation)
                }
                finalizedBy(mergeDetektReports)
            }
            reports.sarif.required = true
        }
        afterEvaluate {
            tasks.named("check") {
                dependsOn(detektAll)
            }
        }
    }

    val fixKMPMetadata = tasks.register<FixKMPMetadata>("fixKMPMetadata")
    tasks.withType<KotlinCompileTool> {
        if (name.startsWith("compile") && name.endsWith("MainKotlinMetadata")) {
            finalizedBy(fixKMPMetadata)
            fixKMPMetadata.configure {
                compileOutputs.from(this@withType.outputs.files)
            }
        }
    }
    tasks.withType<Jar> {
        if (name == "allMetadataJar") {
            mustRunAfter(fixKMPMetadata)
        }
    }
}