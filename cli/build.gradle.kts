@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithHostTests

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinx.serialization)
}

fun KotlinNativeTargetWithHostTests.configureTarget() =
    binaries {
        executable {
            entryPoint = "main"
            baseName = "dependency-update-checker"
        }
    }

kotlin {
    sourceSets {
        macosX64 { configureTarget() }
        macosArm64 { configureTarget() }
        mingwX64 { configureTarget() }
        linuxX64 { configureTarget() }

        jvm {
            binaries {
                executable {
                    mainClass = "com.deezer.dependencies.cli.JvmMainKt"
                    applicationName = "dependency-update-checker"
                }
            }
        }

        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.serialization.toml)
                implementation(libs.clikt)
                implementation(libs.mordant)
                implementation(libs.mordant.coroutines)
                implementation(libs.kotlinx.coroutines.core)
                implementation(projects.core)
            }
        }
        val commonTest by getting {
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
