@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING
import com.deezer.caupain.currentArch
import com.deezer.caupain.tasks.MakeBinariesZipTask
import com.deezer.caupain.tasks.RenameCurrentBinaryTask
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

val renameCurrentBinaryTask = tasks.register<RenameCurrentBinaryTask>("renameCurrentArchBinary")
tasks.register("buildCurrentArchBinary") {
    dependsOn(currentArch.map { "${it.archName}Binaries" }.get())
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
