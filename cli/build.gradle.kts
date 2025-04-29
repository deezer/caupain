@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.gradle.kotlin.dsl.support.zipTo
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithHostTests
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.mokkery)
    alias(libs.plugins.compat.patrouille)
}

fun KotlinNativeTargetWithHostTests.configureTarget() =
    binaries {
        executable(listOf(NativeBuildType.RELEASE)) {
            entryPoint = "main"
            baseName = "caupain"
        }
    }

compatPatrouille {
    java(17)
    kotlin(libs.versions.kotlin.get())
}

kotlin {
    compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")

    sourceSets {
        macosX64 { configureTarget() }
        macosArm64 { configureTarget() }
        mingwX64 { configureTarget() }
        linuxX64 { configureTarget() }

        jvm {
            binaries {
                executable {
                    mainClass = "com.deezer.caupain.cli.JvmMainKt"
                    applicationName = "caupain"
                }
            }
        }

        getByName("commonMain") {
            dependencies {
                implementation(libs.kotlinx.serialization.toml)
                implementation(libs.bundles.clikt)
                implementation(libs.kotlinx.coroutines.core)
                implementation(projects.core)
                implementation(libs.app.dirs)
            }
        }
        getByName("commonTest") {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.okio.fake.filesystem)
                implementation(libs.kotlinx.coroutines.test)
                compileOnly(libs.jetbrains.annotations)
            }
        }
    }
}

tasks.named<JavaExec>("runJvm") {
    workingDir = rootProject.projectDir
}
val zipAndCopyBinaries = tasks.register<MakeBinariesZipTask>("zipAndCopyBinaries") {
    dependsOn(
        "macosX64Binaries",
        "macosArm64Binaries",
        "mingwX64Binaries",
        "linuxX64Binaries"
    )
}
tasks.register("assembleAll") {
    dependsOn(zipAndCopyBinaries, "jvmDistZip")
}

open class MakeBinariesZipTask : DefaultTask() {

    @get:Internal
    val binDir = project.layout.buildDirectory.dir("bin")

    @get:OutputDirectory
    val zipDir = binDir.map { it.dir("zip") }

    @get:InputFiles
    val binFiles = project.files(
        Architectures.values().map { arch ->
            binDir.map { it.file(arch.filePath) }
        }
    )

    @get:Input
    val version = project.version.toString()

    @TaskAction
    fun copyAndZip() {
        val binDir = this.binDir.get().asFile
        val zipDir = this.zipDir.get().asFile
        zipDir.mkdirs()
        val outDir = File(binDir, "caupain")
        outDir.mkdirs()
        for (arch in Architectures.values()) {
            val binaryFile = File(binDir, arch.filePath)
            outDir.listFiles()?.forEach { it.delete() }
            val outFileName = buildString {
                append(binaryFile.nameWithoutExtension)
                if (arch.outExt != null) {
                    append('.')
                    append(arch.outExt)
                }
            }
            val outFile = File(outDir, outFileName)
            binaryFile.copyTo(outFile)
            val zipFile = File(zipDir, "caupain-$version-${arch.platformName}.zip")
            zipTo(zipFile, outDir)
        }
        outDir.deleteRecursively()
    }
}

enum class Architectures(
    val platformName: String,
    private val archName: String,
    private val ext: String,
    val outExt: String?
) {
    MACOS_ARM("macos-silicon", "macosArm64", "kexe", null),
    MACOS_X86("macos-intel", "macosX64", "kexe", null),
    LINUX("linux", "linuxX64", "kexe", null),
    WINDOWS("windows", "mingwX64", "exe", "exe");

    val filePath: String
        get() = "${archName}/releaseExecutable/caupain.${ext}"
}
