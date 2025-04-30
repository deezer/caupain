@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING
import org.gradle.kotlin.dsl.support.zipTo
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithHostTests
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.mokkery)
    alias(libs.plugins.compat.patrouille)
    alias(libs.plugins.buildkonfig)
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

buildkonfig {
    packageName = "com.deezer.caupain"

    defaultConfigs {
        buildConfigField(STRING, "VERSION", version.toString())
    }
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

val osName: String = System.getProperty("os.name")
val currentArch = when {
    osName == "Mac OS X" -> if (System.getProperty("os.arch") == "aarch64") {
        Architecture.MACOS_ARM
    } else {
        Architecture.MACOS_X86
    }

    osName == "Linux" -> Architecture.LINUX

    osName.startsWith("Windows") -> Architecture.WINDOWS

    else -> throw GradleException("Host OS not supported: $osName")
}
val renameCurrentBinaryTask = tasks.register<RenameCurrentBinaryTask>("renameCurrentArchBinary") {
    architecture.set(currentArch)
}
tasks.register("buildCurrentArchBinary") {
    dependsOn("${currentArch.archName}Binaries")
    finalizedBy(renameCurrentBinaryTask)
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

@Suppress("LeakingThis") // This is only abstract to be instantiated by Gradle
abstract class RenameCurrentBinaryTask : Copy() {

    @get:Input
    val architecture = project.objects.property<Architecture>()

    @get:Internal
    val binDir = project.layout.buildDirectory.dir("bin")

    @get:InputFile
    val binaryFile = architecture.flatMap { arch ->
        binDir.map { binDir ->
            binDir.file(arch.filePath)
        }
    }

    init {
        from(binaryFile)
        into(binDir)
        rename { architecture.get().outFileName }
    }
}

open class MakeBinariesZipTask : DefaultTask() {

    @get:Internal
    val binDir = project.layout.buildDirectory.dir("bin")

    @get:OutputDirectory
    val zipDir = binDir.map { it.dir("zip") }

    @get:InputFiles
    val binFiles = project.files(
        Architecture.values().map { arch ->
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
        for (arch in Architecture.values()) {
            val binaryFile = File(binDir, arch.filePath)
            outDir.listFiles()?.forEach { it.delete() }
            val outFileName = arch.outFileName
            val outFile = File(outDir, outFileName)
            binaryFile.copyTo(outFile)
            val zipFile = File(zipDir, "caupain-$version-${arch.platformName}.zip")
            zipTo(zipFile, outDir)
        }
        outDir.deleteRecursively()
    }
}

enum class Architecture(
    val platformName: String,
    val archName: String,
    private val ext: String,
    val outExt: String?
) {
    MACOS_ARM("macos-silicon", "macosArm64", "kexe", null),
    MACOS_X86("macos-intel", "macosX64", "kexe", null),
    LINUX("linux", "linuxX64", "kexe", null),
    WINDOWS("windows", "mingwX64", "exe", "exe");

    val filePath: String
        get() = "${archName}/releaseExecutable/caupain.${ext}"

    val outFileName: String
        get() = buildString {
            append("caupain")
            if (outExt != null) {
                append('.')
                append(outExt)
            }
        }
}
