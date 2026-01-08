@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import com.deezer.caupain.currentArch
import com.deezer.caupain.tasks.RenameCurrentBinaryTask
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.tapmoc)
}

fun KotlinNativeTarget.configureTarget() =
    binaries {
        executable(listOf(NativeBuildType.RELEASE)) {
            entryPoint = "main"
            baseName = "sink-test"
        }
    }

tapmoc {
    java(17)
    kotlin(libs.versions.kotlin.get())
}

kotlin {
    sourceSets {
        macosX64 { configureTarget() }
        macosArm64 { configureTarget() }
        mingwX64 { configureTarget() }
        linuxX64 { configureTarget() }
        linuxArm64 { configureTarget() }
        js {
            binaries.executable()
            nodejs()
        }

        applyDefaultHierarchyTemplate()

        commonMain {
            dependencies {
                implementation(projects.core)
            }
        }
    }
}

val renameCurrentBinaryTask = tasks.register<RenameCurrentBinaryTask>("renameCurrentArchBinary") {
    baseName.set("sink-test")
}
tasks.register("buildCurrentArchBinary") {
    dependsOn(currentArch.map { "${it.fullArchName}Binaries" }.get())
    finalizedBy(renameCurrentBinaryTask)
}
