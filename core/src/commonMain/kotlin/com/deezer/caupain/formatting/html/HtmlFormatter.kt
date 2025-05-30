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

package com.deezer.caupain.formatting.html

import com.deezer.caupain.formatting.FileFormatter
import com.deezer.caupain.formatting.Formatter
import com.deezer.caupain.internal.asAppendable
import com.deezer.caupain.model.DependenciesUpdateResult
import com.deezer.caupain.model.GradleUpdateInfo
import com.deezer.caupain.model.UpdateInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.p
import kotlinx.html.stream.appendHTML
import kotlinx.html.style
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.tr
import kotlinx.html.unsafe
import okio.BufferedSink
import okio.FileSystem
import okio.Path
import okio.SYSTEM

/**
 * HtmlFormatter is a [Formatter] that formats the output as HTML.
 *
 * @param path The path to the HTML file to write.
 * @param fileSystem The file system to use for writing the file. Default uses the native file system.
 * @param ioDispatcher The coroutine dispatcher to use for IO operations. Default is [Dispatchers.IO].
 */
public class HtmlFormatter(
    path: Path,
    fileSystem: FileSystem = FileSystem.SYSTEM,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : FileFormatter(path, fileSystem, ioDispatcher) {

    override suspend fun BufferedSink.writeUpdates(updates: DependenciesUpdateResult) {
        this
            .asAppendable()
            .appendHTML()
            .html {
                head {
                    style {
                        unsafe {
                            raw(STYLE)
                        }
                    }
                }
                body {
                    if (updates.isEmpty()) {
                        h1 { +"No updates available." }
                    } else {
                        h1 { +"Dependency updates" }
                        appendGradleUpdate(updates.gradleUpdateInfo)
                        for ((type, currentUpdates) in updates.updateInfos) {
                            appendDependencyUpdates(type, currentUpdates)
                        }
                    }
                }
            }
    }

    private fun FlowContent.appendGradleUpdate(updateInfo: GradleUpdateInfo?) {
        if (updateInfo == null) return
        h2 { +"Gradle" }
        p {
            +"Gradle current version is ${updateInfo.currentVersion} whereas last version is ${updateInfo.updatedVersion}."
            +" See "
            a(href = updateInfo.url) { +"release note" }
            +"."
        }
    }

    private fun FlowContent.appendDependencyUpdates(
        type: UpdateInfo.Type,
        updates: List<UpdateInfo>
    ) {
        if (updates.isEmpty()) return
        h2 { +type.title }
        p {
            table {
                tr {
                    th { +"Id" }
                    th { +"Name" }
                    th { +"Current version" }
                    th { +"Updated version" }
                    th { +"URL" }
                }
                for (update in updates) {
                    tr {
                        td { +update.dependencyId }
                        td { +update.name.orEmpty() }
                        td { +update.currentVersion }
                        td { +update.updatedVersion }
                        td {
                            update
                                .url
                                ?.let { url ->
                                    a(href = url) { +url }
                                }
                                ?: +""
                        }
                    }
                }
            }
        }
    }

    private companion object {
        private const val STYLE = """
        body {
          background-color: Canvas;
          color: CanvasText;
          color-scheme: light dark;
        }
            
        th,
        td {
          border: 1px solid ButtonBorder;
          padding: 8px 10px;
        }
        
        td {
          text-align: center;
        }
        
        tr:nth-of-type(even) {
          background-color: ButtonFace;
        }
        
        table {
          border-collapse: collapse;
          border: 2px solid ButtonBorder;
          width: 100%;
        }  
        """
    }
}