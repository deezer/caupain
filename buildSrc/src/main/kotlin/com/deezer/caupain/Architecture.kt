/*
 * MIT License
 *
 * Copyright (c) 2025 Deezer
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.deezer.caupain

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.Provider

enum class Architecture(
    val platformName: String,
    val fullArchName: String,
    val shortArchName: String,
    private val ext: String,
    private val outExt: String?
) {
    MACOS_ARM("macos-silicon", "macosArm64", "arm64", "kexe", null),
    MACOS_X86("macos-intel", "macosX64", "amd64", "kexe", null),
    LINUX_X86("linux", "linuxX64", "amd64", "kexe", null),
    LINUX_ARM("linux-arm", "linuxArm64", "arm64", "kexe", null),
    WINDOWS("windows", "mingwX64", "amd64", "exe", "exe");

    fun getFilePath(baseName: String) = "${fullArchName}/releaseExecutable/$baseName.${ext}"

    fun getOutFileName(baseName: String): String = buildString {
        append(baseName)
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

                osName == "Linux" -> if (osArch == "aarch64") {
                    Architecture.LINUX_ARM
                } else {
                    Architecture.LINUX_X86
                }

                osName.startsWith("Windows") -> Architecture.WINDOWS

                else -> throw GradleException("Host OS not supported: $osName")
            }
        }
    }