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
import com.deezer.caupain.formatting.model.Input
import com.deezer.caupain.formatting.model.VersionReferenceInfo
import com.deezer.caupain.internal.asAppendable
import com.deezer.caupain.model.GradleUpdateInfo
import com.deezer.caupain.model.SelfUpdateInfo
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

    override suspend fun BufferedSink.writeUpdates(input: Input) {
        this
            .asAppendable()
            .apply {
                if (input.isEmpty) {
                    appendLine("# No updates available.")
                } else {
                    appendLine("# Dependency updates")
                    appendSelfUpdate(input.selfUpdateInfo)
                    appendGradleUpdate(input.gradleUpdateInfo)
                    appendVersionReferenceUpdates(input.versionReferenceInfo)
                    for ((type, currentUpdates) in input.updateInfos) {
                        appendDependencyUpdates(type, currentUpdates)
                    }
                    appendIgnoredUpdates(input.ignoredUpdateInfos)
                }
            }
    }

    private fun Appendable.appendSelfUpdate(selfUpdateInfo: SelfUpdateInfo?) {
        if (selfUpdateInfo == null) return
        appendLine("## Caupain")
        append("Caupain current version is ")
        append(selfUpdateInfo.currentVersion)
        append(" whereas last version is ")
        appendLine(selfUpdateInfo.updatedVersion)
        append("You can update Caupain via")
        if (selfUpdateInfo.sources.size == 1) {
            append(' ')
            val source = selfUpdateInfo.sources.single()
            if (source.link == null) {
                appendLine(source.description)
            } else {
                append('[')
                append(source.description)
                append("](")
                append(source.link)
                appendLine(')')
            }
        } else {
            appendLine(" :")
            for (source in selfUpdateInfo.sources) {
                append("- ")
                if (source.link == null) {
                    appendLine(source.description)
                } else {
                    append('[')
                    append(source.description)
                    append("](")
                    append(source.link)
                    appendLine(')')
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

    private fun Appendable.appendVersionReferenceUpdates(updates: List<VersionReferenceInfo>?) {
        if (updates.isNullOrEmpty()) return
        appendLine("## Version References")
        appendTable(
            headers = listOf(ID_TITLE, CURRENT_VERSION_TITLE, UPDATED_VERSION_TITLE, "Details"),
            rows = updates.map { update ->
                listOf(
                    update.id,
                    update.currentVersion.toString(),
                    update.updatedVersion.toString(),
                    createVersionReferencesDetails(update)
                )
            }
        )
    }

    private fun createVersionReferencesDetails(update: VersionReferenceInfo) = buildString {
        var hasContent = false
        if (update.fullyUpdatedLibraries.isNotEmpty()) {
            hasContent = true
            append("Libraries: ")
            update.fullyUpdatedLibraries.joinTo(this)
        }
        if (update.fullyUpdatedPlugins.isNotEmpty()) {
            if (hasContent) append(LINE_BREAK)
            hasContent = true
            append("Plugins: ")
            update.fullyUpdatedPlugins.joinTo(this)
        }
        if (!update.isFullyUpdated) {
            if (hasContent) append(LINE_BREAK)
            append("Updates for these dependency using the reference were not found for the updated version:")
            append("<ul>")
            for (key in update.libraryKeys) {
                val updatedVersion = update.updatedLibraries[key]
                if (updatedVersion != update.updatedVersion) {
                    append("<li>")
                    append(key)
                    append(": ")
                    append(updatedVersion?.toString() ?: "(no update found)")
                    append("</li>")
                }
            }
            for (key in update.pluginKeys) {
                val updatedVersion = update.updatedPlugins[key]
                if (updatedVersion != update.updatedVersion) {
                    append("<li>")
                    append(key)
                    append(": ")
                    append(updatedVersion?.toString() ?: "(no update found)")
                    append("</li>")
                }
            }
            append("</ul>")
        }
    }

    private fun Appendable.appendDependencyUpdates(
        type: UpdateInfo.Type,
        updates: List<UpdateInfo>
    ) {
        if (updates.isEmpty()) return
        append("## ")
        appendLine(type.title)
        appendTable(
            headers = listOf(ID_TITLE, "Name", CURRENT_VERSION_TITLE, UPDATED_VERSION_TITLE, "URL"),
            rows = updates.map { update ->
                listOf(
                    update.dependencyId,
                    update.name.orEmpty(),
                    update.currentVersion.toString(),
                    update.updatedVersion.toString(),
                    buildString {
                        var hasContent = false
                        if (update.releaseNoteUrl != null) {
                            hasContent = true
                            append("[Release notes](")
                            append(update.releaseNoteUrl)
                            append(")")
                        }
                        if (update.url != null) {
                            if (hasContent) append(LINE_BREAK)
                            append("[Project](")
                            append(update.url)
                            append(')')
                        }
                    }
                )
            }
        )
    }

    private fun Appendable.appendIgnoredUpdates(updates: List<UpdateInfo>) {
        if (updates.isEmpty()) return
        append("## ")
        appendLine("Ignored")
        appendTable(
            headers = listOf(ID_TITLE, CURRENT_VERSION_TITLE, UPDATED_VERSION_TITLE),
            rows = updates.map { update ->
                listOf(
                    update.dependencyId,
                    update.currentVersion.toString(),
                    update.updatedVersion.toString()
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

    private companion object {
        private const val ID_TITLE = "Id"
        private const val CURRENT_VERSION_TITLE = "Current version"
        private const val UPDATED_VERSION_TITLE = "Updated version"
        private const val LINE_BREAK = "<br/>"
    }
}