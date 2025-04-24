@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import com.deezer.caupain.tasks.MakeBinariesZipTask
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithHostTests
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.mokkery)
}

fun KotlinNativeTargetWithHostTests.configureTarget() =
    binaries {
        executable(listOf(NativeBuildType.RELEASE)) {
            entryPoint = "main"
            baseName = "caupain"
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
                implementation(libs.clikt)
                implementation(libs.mordant)
                implementation(libs.mordant.coroutines)
                implementation(libs.kotlinx.coroutines.core)
                implementation(projects.core)
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

enum class Architectures(val archName: String, val ext: String, val outExt: String?) {
    MACOS_ARM("macosArm64", "kexe", null),
    MACOS_X86("macosX64", "kexe", null),
    LINUX("linuxX64", "kexe", null),
    WINDOWS("mingwX64", "exe", "exe"),
}
