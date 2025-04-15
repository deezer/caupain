@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithHostTests

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinx.serialization)
}

val mainApplicationClass = "com.deezer.dependencies.cli.JvmMainKt"

fun KotlinNativeTargetWithHostTests.configureTarget() =
    binaries { executable { entryPoint = "main" } }

kotlin {
    sourceSets {
        macosX64 { configureTarget() }
        macosArm64 { configureTarget() }
        mingwX64 { configureTarget() }
        linuxX64 { configureTarget() }

        jvm {
            binaries {
                executable {
                    mainClass = mainApplicationClass
                }
            }
        }

        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.serialization.toml)
                implementation(libs.okio)
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
