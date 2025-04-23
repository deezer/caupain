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
        appendLine("| Id | Name | Current version | Updated version | URL |")
        appendLine("|----|------|----------------|------------------|-----|")
        for (update in updates) {
            append("| ")
            append(update.dependencyId)
            append(" | ")
            append(update.name.orEmpty())
            append(" | ")
            append(update.currentVersion)
            append(" | ")
            append(update.updatedVersion)
            append(" | ")
            update
                .url
                ?.let { url ->
                    append("[")
                    append(url)
                    append("](")
                    append(url)
                    appendLine(") |")
                }
                ?: appendLine(" |")
        }
    }
}