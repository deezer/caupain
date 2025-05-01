import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompileTool

plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinx.serialization) apply false
    alias(libs.plugins.jetbrains.kotlin.jvm) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.changelog)
}

val currentVersion = "0.1.0"

val isSnapshot = project.findProperty("isSnapshot")?.toString().toBoolean()
val isCI = System.getenv("CI").toBoolean()
version = buildString {
    append(currentVersion)
    if (isSnapshot || !isCI) append(".0-SNAPSHOT")
}

subprojects {
    group = "com.deezer.caupain"
    version = rootProject.version

    apply(plugin = "io.gitlab.arturbosch.detekt")

    extensions.configure<DetektExtension> {
        config.setFrom(rootProject.layout.projectDirectory.file("code-quality/detekt.yml"))
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
    afterEvaluate {
        tasks.named("check") {
            dependsOn(detektAll)
        }
    }
}

open class FixKMPMetadata : DefaultTask() {
    @get:Internal
    val compileOutputs = project.objects.fileCollection()

    @get:Input
    val groupId = project.group.toString()

    @get:InputFiles
    val manifestFiles: FileCollection
        get() = compileOutputs.asFileTree.filter { it.isFile && it.name == "manifest" }

    @get:OutputFiles
    val outputFile: FileCollection
        get() = manifestFiles

    @TaskAction
    fun fixUniqueName() {
        manifestFiles.forEach { manifestFile ->
            val content = manifestFile.useLines { lines ->
                lines
                    .filterNot { it.isBlank() }
                    .map { line ->
                        val iEq = line.indexOf('=')
                        require(iEq != -1) {
                            "Metadata manifest file contents invalid. Contains invalid key-value-pair '$line'"
                        }
                        line.substring(0, iEq) to line.substring(iEq + 1)
                    }
                    .toMap(mutableMapOf())
            }
            val old = content["unique_name"] ?: return
            val prefix = "$groupId\\:"
            if (old.startsWith(prefix)) return
            val new = "$prefix$old"
            content["unique_name"] = new

            manifestFile.bufferedWriter().use { writer ->
                for ((key, value) in content) {
                    writer.append(key)
                    writer.append('=')
                    writer.appendLine(value)
                }
            }
        }
    }
}