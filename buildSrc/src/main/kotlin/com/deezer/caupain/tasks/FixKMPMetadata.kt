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
 *
 * This file incorporates work covered by the following copyright and permission notice:
 *
 * Copyright (c) 2024 Matthew Nelson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.deezer.caupain.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction

open class FixKMPMetadata : DefaultTask() {
    @get:Internal
    val compileOutputs = project.objects.fileCollection()

    @get:Input
    val groupId = project.group.toString()

    @get:InputFiles
    val manifestFiles: FileCollection
        get() = compileOutputs.asFileTree.filter { it.isFile && it.name == "manifest" }

    @get:OutputFiles
    val outputFile: FileCollection
        get() = manifestFiles

    @TaskAction
    fun fixUniqueName() {
        manifestFiles.forEach { manifestFile ->
            val content = manifestFile.useLines { lines ->
                lines
                    .filterNot { it.isBlank() }
                    .map { line ->
                        val iEq = line.indexOf('=')
                        require(iEq != -1) {
                            "Metadata manifest file contents invalid. Contains invalid key-value-pair '$line'"
                        }
                        line.substring(0, iEq) to line.substring(iEq + 1)
                    }
                    .toMap(mutableMapOf())
            }
            val old = content["unique_name"] ?: return
            val prefix = "$groupId\\:"
            if (old.startsWith(prefix)) return
            val new = "$prefix$old"
            content["unique_name"] = new

            manifestFile.bufferedWriter().use { writer ->
                for ((key, value) in content) {
                    writer.append(key)
                    writer.append('=')
                    writer.appendLine(value)
                }
            }
        }
    }
}