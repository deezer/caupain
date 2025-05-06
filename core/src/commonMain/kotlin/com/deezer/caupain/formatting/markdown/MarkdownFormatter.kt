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

package com.deezer.caupain.formatting.markdown

import com.deezer.caupain.formatting.FileFormatter
import com.deezer.caupain.formatting.Formatter
import com.deezer.caupain.internal.asAppendable
import com.deezer.caupain.model.DependenciesUpdateResult
import com.deezer.caupain.model.GradleUpdateInfo
import com.deezer.caupain.model.UpdateInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import okio.BufferedSink
import okio.FileSystem
import okio.Path
import okio.SYSTEM

/**
 * MarkdownFormatter is a [Formatter] that formats the output as Markdown.
 *
 * @param path The path to the Markdown file to write.
 * @param fileSystem The file system to use for writing the file. Default uses the native file system.
 * @param ioDispatcher The coroutine dispatcher to use for IO operations. Default is [Dispatchers.IO].
 */
public class MarkdownFormatter(
    path: Path,
    fileSystem: FileSystem = FileSystem.SYSTEM,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : FileFormatter(path, fileSystem, ioDispatcher) {

    override suspend fun BufferedSink.writeUpdates(updates: DependenciesUpdateResult) {
        this
            .asAppendable()
            .apply {
                if (updates.isEmpty()) {
                    appendLine("# No updates available.")
                } else {
                    appendLine("# Dependency updates")
                    appendGradleUpdate(updates.gradleUpdateInfo)
                    for ((type, currentUpdates) in updates.updateInfos) {
                        appendDependencyUpdates(type, currentUpdates)
                    }
                }
            }
    }

    private fun Appendable.appendGradleUpdate(updateInfo: GradleUpdateInfo?) {
        if (updateInfo == null) return
        appendLine("## Gradle")
        append("Gradle current version is ")
        append(updateInfo.currentVersion)
        append(" whereas last version is ")
        append(updateInfo.updatedVersion)
        append(". See [")
        append(updateInfo.url)
        append("](")
        append(updateInfo.url)
        appendLine(").")
    }

    private fun Appendable.appendDependencyUpdates(
        type: UpdateInfo.Type,
        updates: List<UpdateInfo>
    ) {
        if (updates.isEmpty()) return
        append("## ")
        appendLine(type.title)
        appendTable(
            headers = listOf("Id", "Name", "Current version", "Updated version", "URL"),
            rows = updates.map { update ->
                listOf(
                    update.dependencyId,
                    update.name.orEmpty(),
                    update.currentVersion,
                    update.updatedVersion,
                    buildString {
                        if (update.url != null) {
                            append("[")
                            append(update.url)
                            append("](")
                            append(update.url)
                            append(')')
                        }
                    }
                )
            }
        )
    }

    private fun Appendable.appendTable(
        headers: List<String>,
        rows: List<List<String>>
    ) {
        // Compute each column width
        val columnWidths = IntArray(headers.size) { index ->
            maxOf(headers[index].length, rows.maxOf { it[index].length })
        }
        // Append headers
        appendTableLine(headers, columnWidths)
        // Append separator line
        val separators = List(headers.size) { index ->
            buildString {
                repeat(columnWidths[index]) { append('-') }
            }
        }
        appendTableLine(separators, columnWidths)
        // Append rows
        for (row in rows) appendTableLine(row, columnWidths)
    }

    private fun Appendable.appendTableLine(line: List<String>, columnWidths: IntArray) {
        append("| ")
        for (i in line.indices) {
            if (i > 0) append(" | ")
            appendPadded(line[i], columnWidths[i])
        }
        appendLine(" |")
    }

    private fun Appendable.appendPadded(value: String, width: Int) {
        append(value)
        repeat(width - value.length) { append(' ') }
    }
}