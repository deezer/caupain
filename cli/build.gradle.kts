@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithHostTests

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.mokkery)
}

fun KotlinNativeTargetWithHostTests.configureTarget() =
    binaries {
        executable {
            entryPoint = "main"
            baseName = "caupain"
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
val detektAll = tasks.register("detektAll") {
    group = "verification"
    description = "Run detekt analysis for all targets"
    dependsOn(tasks.withType<Detekt>())
}
tasks.named("check") {
    dependsOn(detektAll)
}
