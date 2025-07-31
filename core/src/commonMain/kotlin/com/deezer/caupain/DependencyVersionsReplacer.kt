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

import com.deezer.caupain.model.DependenciesUpdateResult
import com.deezer.caupain.model.Dependency
import com.deezer.caupain.model.GradleDependencyVersion
import com.deezer.caupain.model.UpdateInfo
import com.deezer.caupain.model.VersionCatalogInfo
import com.deezer.caupain.model.versionCatalog.Version
import com.deezer.caupain.model.versionCatalog.VersionCatalog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.BufferedSink
import okio.BufferedSource
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import okio.buffer
import okio.use
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

public interface DependencyVersionsReplacer {
    public suspend fun replaceVersions(
        versionCatalogPath: Path,
        updateResult: DependenciesUpdateResult,
    )
}

public fun DependencyVersionsReplacer(
    fileSystem: FileSystem = FileSystem.SYSTEM,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
): DependencyVersionsReplacer = DefaultDependencyVersionsReplacer(
    fileSystem = fileSystem,
    ioDispatcher = ioDispatcher,
    defaultDispatcher = defaultDispatcher
)

@OptIn(ExperimentalUuidApi::class)
internal class DefaultDependencyVersionsReplacer(
    private val fileSystem: FileSystem,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) : DependencyVersionsReplacer {
    override suspend fun replaceVersions(
        versionCatalogPath: Path,
        updateResult: DependenciesUpdateResult,
    ) {
        // First, let's compute the replacements
        val replacements = withContext(defaultDispatcher) {
            computeReplacements(
                updatedLibraryVersions = updateResult
                    .updateInfos[UpdateInfo.Type.LIBRARY]
                    ?.associate { it.dependency to it.updatedVersion }
                    .orEmpty(),
                updatedPluginsVersions = updateResult
                    .updateInfos[UpdateInfo.Type.PLUGIN]
                    ?.associate { it.dependency to it.updatedVersion }
                    .orEmpty(),
                versionCatalog = updateResult.versionCatalog ?: return@withContext null,
                positions = updateResult.versionCatalogInfo?.positions ?: return@withContext null,
            )
        }
        if (replacements.isNullOrEmpty()) return
        withContext(ioDispatcher) {
            // Now, let's read the file and apply the replacements
            val tmpFileName = buildString {
                append(versionCatalogPath.name)
                append('-')
                append(Uuid.Companion.random().toString())
                append(".tmp")
            }
            val tmpOutPath = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / tmpFileName
            writeReplacedFile(tmpOutPath, versionCatalogPath, replacements)
            // Finally, replace the original file with the temporary one
            fileSystem.delete(versionCatalogPath)
            fileSystem.atomicMove(tmpOutPath, versionCatalogPath)
        }
    }

    private fun computeReplacements(
        updatedLibraryVersions: Map<String, GradleDependencyVersion.Static>,
        updatedPluginsVersions: Map<String, GradleDependencyVersion.Static>,
        versionCatalog: VersionCatalog,
        positions: VersionCatalogInfo.Positions,
    ): List<Replacement> = buildMap {
        for ((key, updatedVersion) in updatedLibraryVersions) {
            addReplacementIfNecessary(
                type = Type.LIBRARIES,
                key = key,
                updatedVersion = updatedVersion,
                dependency = versionCatalog.libraries[key] ?: continue,
                depPositions = positions.libraryVersionPositions,
                versionRefPositions = positions.versionRefsPositions,
                versionRefVersions = versionCatalog.versions
            )
        }
        for ((key, updatedVersion) in updatedPluginsVersions) {
            addReplacementIfNecessary(
                type = Type.PLUGINS,
                key = key,
                updatedVersion = updatedVersion,
                dependency = versionCatalog.plugins[key] ?: continue,
                depPositions = positions.pluginVersionPositions,
                versionRefPositions = positions.versionRefsPositions,
                versionRefVersions = versionCatalog.versions
            )
        }
    }.values.sorted()

    private fun MutableMap<ReplacementKey, Replacement>.addReplacementIfNecessary(
        type: Type,
        key: String,
        updatedVersion: GradleDependencyVersion.Static,
        dependency: Dependency,
        depPositions: Map<String, VersionCatalogInfo.VersionPosition>,
        versionRefPositions: Map<String, VersionCatalogInfo.VersionPosition>,
        versionRefVersions: Map<String, Version.Resolved>,
    ) {
        when (val version = dependency.version) {
            null -> return

            is Version.Reference -> {
                val versionKey = version.ref
                val position = versionRefPositions[versionKey] ?: return
                val currentVersionText = versionRefVersions[versionKey]?.toString()
                    ?: return
                val updatedVersionText = updatedVersion.toString()
                put(
                    ReplacementKey(Type.VERSIONS, versionKey),
                    Replacement(
                        position = position,
                        currentVersionText = currentVersionText,
                        updatedVersionText = updatedVersionText
                    )
                )
            }

            is Version.Resolved -> {
                val position = depPositions[key] ?: return
                val currentVersionText = dependency.version.toString()
                val updatedVersionText = updatedVersion.toString()
                put(
                    ReplacementKey(type, key),
                    Replacement(
                        position = position,
                        currentVersionText = currentVersionText,
                        updatedVersionText = updatedVersionText
                    )
                )
            }
        }
    }

    @Suppress("NestedBlockDepth")
    private fun writeReplacedFile(
        tmpOutPath: Path,
        versionCatalogPath: Path,
        replacements: Iterable<Replacement>
    ) {
        fileSystem.sink(tmpOutPath).buffer().use { sink ->
            fileSystem.source(versionCatalogPath).buffer().use { source ->
                val replacementIt = replacements.iterator()
                var currentReplacement: Replacement? = replacementIt.nextOrNull()
                var currentLine = source.readUtf8LineWithBreaks()
                var lineIndex = 0
                while (currentLine != null) {
                    val replacement = currentReplacement
                    if (replacement == null) {
                        // If there is no replacement left, just write the rest of the file
                        sink.writeUtf8(currentLine)
                        sink.writeAll(source)
                        break
                    } else {
                        if (lineIndex < replacement.position.startPoint.line) {
                            // We're between replacements, so we can write the full line
                            sink.writeUtf8(currentLine)
                        } else {
                            // We need to replace the current line
                            val linesToSkip = sink.writeReplacement(
                                replacement = replacement,
                                currentLine = currentLine
                            )
                            repeat(linesToSkip) {
                                // Skip the next lines as they are part of the replacement
                                source.readUtf8Line()
                                lineIndex++
                            }
                            // Prepare for the next replacement
                            currentReplacement = null
                        }
                    }
                    currentLine = source.readUtf8LineWithBreaks()
                    if (currentReplacement == null) currentReplacement = replacementIt.nextOrNull()
                    lineIndex++
                }
            }
        }
    }

    private fun BufferedSink.writeReplacement(
        replacement: Replacement,
        currentLine: String,
    ): Int {
        // Replace the version text
        val replacedText = replacement.position.valueText.replace(
            oldValue = replacement.currentVersionText,
            newValue = replacement.updatedVersionText
        )
        val nbLines = replacement.position.nbLines
        return if (nbLines <= 1) {
            // Replacement is on the same line, just replace it in the line to
            // keep the line breaks intact
            writeUtf8(
                currentLine.replace(
                    oldValue = replacement.position.valueText,
                    newValue = replacedText
                )
            )
            // No lines to skip, as we replaced the whole line
            0
        } else {
            // Write up to the start of the replacement
            writeUtf8(currentLine, 0, replacement.position.startPoint.column - 1)
            // Write the replaced text
            writeUtf8(replacedText)
            // We need to skip the rest of the lines that were replaced
            nbLines - 1
        }
    }

    private data class Replacement(
        val position: VersionCatalogInfo.VersionPosition,
        val currentVersionText: String,
        val updatedVersionText: String
    ) : Comparable<Replacement> {

        override fun compareTo(other: Replacement): Int {
            return position.startPoint.compareTo(other.position.startPoint)
        }
    }

    private enum class Type {
        VERSIONS, LIBRARIES, PLUGINS
    }

    private data class ReplacementKey(
        val type: Type,
        val key: String
    )

    private companion object Companion {
        private val LINE_RETURN = '\n'.code.toByte()

        private fun BufferedSource.readUtf8LineWithBreaks(): String? {
            val newLine = indexOf(LINE_RETURN)
            return if (newLine == -1L) {
                if (buffer.size != 0L) {
                    readUtf8(buffer.size)
                } else {
                    null
                }
            } else {
                // Read up to the new line, including the line break
                readUtf8(newLine + 1)
            }
        }

        @Suppress("NOTHING_TO_INLINE")
        private inline fun <T> Iterator<T>.nextOrNull(): T? = if (hasNext()) next() else null
    }
}