package com.deezer.caupain

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.Provider

enum class Architecture(
    val platformName: String,
    val archName: String,
    private val ext: String,
    private val outExt: String?
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

private val Project.osName: Provider<String>
    get() = providers.systemProperty("os.name")

private val Project.osArch: Provider<String>
    get() = providers.systemProperty("os.arch")

val Project.currentArch: Provider<Architecture>
    get() = osName.flatMap { osName ->
        osArch.map { osArch ->
            when {
                osName == "Mac OS X" -> if (osArch == "aarch64") {
                    Architecture.MACOS_ARM
                } else {
                    Architecture.MACOS_X86
                }

                osName == "Linux" -> Architecture.LINUX

                osName.startsWith("Windows") -> Architecture.WINDOWS

                else -> throw GradleException("Host OS not supported: $osName")
            }
        }
    }