@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING
import com.deezer.caupain.Architecture
import com.deezer.caupain.currentArch
import com.deezer.caupain.rename
import com.deezer.caupain.tasks.CreateChocolateyFilesTask
import com.deezer.caupain.tasks.MakeBinariesZipTask
import com.deezer.caupain.tasks.RenameCurrentBinaryTask
import com.netflix.gradle.plugins.deb.Deb
import com.netflix.gradle.plugins.packaging.ProjectPackagingExtension
import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.redline_rpm.header.Os

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.mokkery)
    alias(libs.plugins.compat.patrouille)
    alias(libs.plugins.buildkonfig)
    alias(libs.plugins.netflix.nebula.ospackage)
    alias(libs.plugins.dependency.guard)
}

fun KotlinNativeTarget.configureTarget() =
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
        linuxArm64 { configureTarget() }

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

dependencyGuard {
    configuration("jvmMainCompileClasspath")
    configuration("jvmMainRuntimeClasspath")
    configuration("metadataCommonMainCompileClasspath")
    configuration("metadataNativeMainCompileClasspath")
}

tasks.withType<Detekt> {
    exclude("**/BuildKonfig.kt")
}

ospackage {
    packageName = "caupain"
    packageGroup = "devel"
    maintainer = "Deezer <androidteam@deezer.com>"
    distribution = "stable"
    packageDescription = "CLI tool to manage Gradle version catalog updates"
    url = "https://github.com/deezer/caupain"
    release = "1"
    os = Os.LINUX

    into("/usr")

    // Bash completion
    from(layout.projectDirectory.file("completions/bash-completion.sh")) {
        into("share/bash-completion/completions")
        rename("caupain")
        filePermissions {
            user {
                read = true
                write = true
            }
            group.read = true
            other.read = true
        }
    }
    // Zsh completion
    from(layout.projectDirectory.file("completions/zsh-completion.sh")) {
        into("share/zsh/vendor-completions")
        rename("_caupain")
        filePermissions {
            user {
                read = true
                write = true
            }
            group.read = true
            other.read = true
        }
    }
}

fun ProjectPackagingExtension.from(sourcePath: Any, configure: CopySpec.() -> Unit) {
    from(sourcePath, closureOf(configure))
}

val buildAllDebs = tasks.register("buildAllDebs")
val linuxArchs = listOf(Architecture.LINUX_X86, Architecture.LINUX_ARM)
for (arch in linuxArchs) {
    val buildLinuxBinariesTask = tasks.named("${arch.fullArchName}Binaries")
    val buildDebTask = tasks.register<Deb>("build${arch.fullArchName}Deb") {
        dependsOn(buildLinuxBinariesTask)
        archStr = arch.shortArchName
        from(layout.buildDirectory.dir("bin/${arch.fullArchName}/releaseExecutable/caupain.kexe")) {
            into("bin")
            rename("caupain")
            filePermissions {
                user {
                    read = true
                    write = true
                    execute = true
                }
                group {
                    read = true
                    execute = true
                }
                other {
                    read = true
                    execute = true
                }
            }
        }
    }
    buildAllDebs {
        dependsOn(buildDebTask)
    }
}

val createChocolateyFilesTask = tasks.register<CreateChocolateyFilesTask>("createChocolateyFiles") {
    id = "caupain"
    title = "Caupain"
    authors = "Deezer"
    summary = "CLI tool to manage Gradle version catalog updates"
    descriptionFile = project.layout.projectDirectory.file("chocolatey/description.md")
    tags.addAll("gradle", "dependencies")
    repositoryUrl = "https://github.com/deezer/caupain"
    licenseUrl = "https://opensource.org/license/mit"
}
tasks.register<Copy>("buildChoco") {
    dependsOn("mingwX64Binaries", createChocolateyFilesTask)
    into(layout.buildDirectory.dir("distributions/chocolatey/tools/bin"))
    from(layout.buildDirectory.dir("bin/mingwX64/releaseExecutable/caupain.exe"))
}

val renameCurrentBinaryTask = tasks.register<RenameCurrentBinaryTask>("renameCurrentArchBinary")
tasks.register("buildCurrentArchBinary") {
    dependsOn(currentArch.map { "${it.fullArchName}Binaries" }.get())
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
        "linuxX64Binaries",
        "linuxArm64Binaries",
    )
}
tasks.register("assembleAll") {
    dependsOn(zipAndCopyBinaries, "jvmDistZip")
}
