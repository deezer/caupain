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

package com.deezer.caupain.tasks

import com.deezer.caupain.Architecture
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.support.zipTo
import java.io.File

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