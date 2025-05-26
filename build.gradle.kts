import com.deezer.caupain.tasks.FixKMPMetadata
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import io.gitlab.arturbosch.detekt.report.ReportMergeTask
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

val currentVersion = "1.1.1"

val isSnapshot = project.findProperty("isSnapshot")?.toString().toBoolean()
val isRelease = project.findProperty("isRelease")?.toString().toBoolean()
val isCI = System.getenv("CI").toBoolean()
version = buildString {
    append(currentVersion)
    if (!isRelease && (isSnapshot || !isCI)) append(".0-SNAPSHOT")
}

changelog {
    version.set(currentVersion)
}

val mergeDetektReports = tasks.register<ReportMergeTask>("mergeDetektReports") {
    output = layout.buildDirectory.file("reports/detekt/merged.sarif")
}

subprojects {
    group = "com.deezer.caupain"
    version = rootProject.version

    apply(plugin = "io.gitlab.arturbosch.detekt")

    extensions.configure<DetektExtension> {
        config.from(rootProject.layout.projectDirectory.file("code-quality/detekt.yml"))
        basePath = rootDir.absolutePath
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
                input.from(this@withType.sarifReportFile)
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

    apply(plugin = "com.dropbox.dependency-guard")
}