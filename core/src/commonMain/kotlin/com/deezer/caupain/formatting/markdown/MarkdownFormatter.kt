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