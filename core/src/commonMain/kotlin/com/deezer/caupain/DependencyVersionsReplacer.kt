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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okio.BufferedSink
import okio.BufferedSource
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.SYSTEM
import okio.buffer
import okio.use
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import com.deezer.caupain.model.versionCatalog.Version.Reference as VersionCatalogVersionReference
import com.deezer.caupain.model.versionCatalog.Version.Resolved as ResolvedVersionCatalogVersion
import com.deezer.caupain.model.versionCatalog.Version.Simple as SimpleVersionCatalogVersion

/**
 * Interface for replacing dependency versions inline in a Gradle version catalog. This takes information
 * produces by the [DependencyUpdateChecker], and uses it to find which part of the version catalog
 * should be replaced with the new version.
 */
public interface DependencyVersionsReplacer {

    /**
     * Replaces the versions in the given version catalog file with the updated versions from the
     * provided [DependenciesUpdateResult]. The version catalog file is expected to be in the format
     * used by Gradle version catalogs (e.g., `libs.versions.toml`
     */
    public suspend fun replaceVersions(
        versionCatalogPath: Path,
        updateResult: DependenciesUpdateResult,
    ) {
        replaceVersions(
            versionCatalogPath = versionCatalogPath,
            input = Input(updateResult)
        )
    }

    /**
     * Replaces the versions in the given version catalog file with the updated versions from the
     * provided [Input]. The version catalog file is expected to be in the format used by
     * Gradle version catalogs (e.g., `libs.versions.toml`).
     */
    public suspend fun replaceVersions(
        versionCatalogPath: Path,
        input: Input,
    )

    /**
     * Version replacement input, which contains a mix of info from dependencies updates and original
     * version catalog.
     */
    @Serializable
    public class Input(
        public val originalLibraryVersions: Map<String, Version>,
        public val updatedLibraryVersions: Map<String, GradleDependencyVersion.Static>,
        public val originalPluginVersions: Map<String, Version>,
        public val updatedPluginsVersions: Map<String, GradleDependencyVersion.Static>,
        public val positions: VersionCatalogInfo.Positions,
        public val versions: Map<String, SimpleVersionCatalogVersion>
    ) {
        public constructor(result: DependenciesUpdateResult) : this(
            originalLibraryVersions = result
                .versionCatalog
                ?.libraries
                ?.mapValuesNotNull { Version.from(it) }
                .orEmpty(),
            updatedLibraryVersions = result
                .updateInfos[UpdateInfo.Type.LIBRARY]
                ?.associate { it.dependency to it.updatedVersion }
                .orEmpty(),
            originalPluginVersions = result
                .versionCatalog
                ?.plugins
                ?.mapValuesNotNull { Version.from(it) }
                .orEmpty(),
            updatedPluginsVersions = result
                .updateInfos[UpdateInfo.Type.PLUGIN]
                ?.associate { it.dependency to it.updatedVersion }
                .orEmpty(),
            versions = result
                .versionCatalog
                ?.versions
                ?.filterValueIsInstance<String, ResolvedVersionCatalogVersion, SimpleVersionCatalogVersion>()
                .orEmpty(),
            positions = result.versionCatalogInfo?.positions ?: VersionCatalogInfo.Positions()
        )

        /**
         * Version catalog version.
         */
        @Serializable
        public sealed class Version {

            /**
             * Version reference
             */
            @Serializable
            public class Reference(public val ref: String) : Version() {
                override fun equals(other: Any?): Boolean {
                    if (this === other) return true
                    if (other == null || this::class != other::class) return false

                    other as Reference

                    return ref == other.ref
                }

                override fun hashCode(): Int {
                    return ref.hashCode()
                }

                override fun toString(): String {
                    return "Reference(ref='$ref')"
                }
            }

            /**
             * Resolved version.
             */
            @Serializable
            public class Resolved(public val versionText: String) : Version() {
                override fun equals(other: Any?): Boolean {
                    if (this === other) return true
                    if (other == null || this::class != other::class) return false

                    other as Resolved

                    return versionText == other.versionText
                }

                override fun hashCode(): Int {
                    return versionText.hashCode()
                }

                override fun toString(): String {
                    return "Resolved(versionText='$versionText')"
                }
            }

            internal companion object {
                fun from(dependency: Dependency) = when (val version = dependency.version) {
                    null -> null
                    is VersionCatalogVersionReference -> Reference(version.ref)
                    is SimpleVersionCatalogVersion -> Resolved(version.toString())
                    else -> null
                }
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as Input

            if (originalLibraryVersions != other.originalLibraryVersions) return false
            if (updatedLibraryVersions != other.updatedLibraryVersions) return false
            if (originalPluginVersions != other.originalPluginVersions) return false
            if (updatedPluginsVersions != other.updatedPluginsVersions) return false
            if (positions != other.positions) return false
            if (versions != other.versions) return false

            return true
        }

        override fun hashCode(): Int {
            var result = originalLibraryVersions.hashCode()
            result = 31 * result + updatedLibraryVersions.hashCode()
            result = 31 * result + originalPluginVersions.hashCode()
            result = 31 * result + updatedPluginsVersions.hashCode()
            result = 31 * result + positions.hashCode()
            result = 31 * result + versions.hashCode()
            return result
        }

        override fun toString(): String {
            return "Input(originalLibraryVersions=$originalLibraryVersions, updatedLibraryVersions=$updatedLibraryVersions, originalPluginVersions=$originalPluginVersions, updatedPluginsVersions=$updatedPluginsVersions, positions=$positions, versions=$versions)"
        }
    }
}

/**
 * Creates a new instance of [DependencyVersionsReplacer].
 */
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
        input: DependencyVersionsReplacer.Input,
    ) {
        // First, let's compute the replacements
        val replacements = withContext(defaultDispatcher) {
            computeReplacements(input)
        }
        if (replacements.isEmpty()) return
        withContext(ioDispatcher) {
            // Now, let's read the file and apply the replacements
            val tmpOutPath = createTempPath(versionCatalogPath, ".tmp")
            writeReplacedFile(tmpOutPath, versionCatalogPath, replacements)
            // Finally, replace the original file with the temporary one
            val backupPath = createTempPath(versionCatalogPath, ".bak")
            fileSystem.atomicMove(versionCatalogPath, backupPath)
            try {
                fileSystem.atomicMove(tmpOutPath, versionCatalogPath)
            } catch (e: IOException) {
                // If the move fails, we need to restore the original file and delete the temporary file
                fileSystem.atomicMove(backupPath, versionCatalogPath)
                fileSystem.delete(tmpOutPath)
                throw e
            }
            fileSystem.delete(backupPath)
        }
    }

    private fun computeReplacements(input: DependencyVersionsReplacer.Input): List<Replacement> =
        buildMap {
            for ((key, updatedVersion) in input.updatedLibraryVersions) {
                addReplacementIfNecessary(
                    type = Type.LIBRARIES,
                    key = key,
                    updatedVersion = updatedVersion,
                    definedVersion = input.originalLibraryVersions[key] ?: continue,
                    depPositions = input.positions.libraryVersionPositions,
                    versionRefPositions = input.positions.versionRefsPositions,
                    versionRefVersions = input.versions
                )
            }
            for ((key, updatedVersion) in input.updatedPluginsVersions) {
                addReplacementIfNecessary(
                    type = Type.PLUGINS,
                    key = key,
                    updatedVersion = updatedVersion,
                    definedVersion = input.originalPluginVersions[key] ?: continue,
                    depPositions = input.positions.pluginVersionPositions,
                    versionRefPositions = input.positions.versionRefsPositions,
                    versionRefVersions = input.versions
                )
            }
        }.values.sorted()

    private fun MutableMap<ReplacementKey, Replacement>.addReplacementIfNecessary(
        type: Type,
        key: String,
        updatedVersion: GradleDependencyVersion.Static,
        definedVersion: DependencyVersionsReplacer.Input.Version,
        depPositions: Map<String, VersionCatalogInfo.VersionPosition>,
        versionRefPositions: Map<String, VersionCatalogInfo.VersionPosition>,
        versionRefVersions: Map<String, SimpleVersionCatalogVersion>,
    ) {
        when (definedVersion) {
            is DependencyVersionsReplacer.Input.Version.Reference -> {
                val versionKey = definedVersion.ref
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

            is DependencyVersionsReplacer.Input.Version.Resolved -> {
                val position = depPositions[key] ?: return
                val currentVersionText = definedVersion.versionText
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

    private fun createTempPath(base: Path, suffix: String): Path {
        val parent = requireNotNull(base.parent) { "Base path must have a parent directory" }
        while (true) {
            val fileName = buildString {
                append(base.name)
                append('-')
                append(Uuid.Companion.random().toString())
                append(suffix)
            }
            val path = parent / fileName
            if (!fileSystem.exists(path)) return path
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

private inline fun <K, V, R : Any> Map<K, V>.mapValuesNotNull(transform: (V) -> R?): Map<K, R> {
    return buildMap {
        for ((key, value) in this@mapValuesNotNull) {
            transform(value)?.let { put(key, it) }
        }
    }
}

private inline fun <K, V, reified T : V> Map<K, V>.filterValueIsInstance(): Map<K, T> {
    return buildMap {
        for ((key, value) in this@filterValueIsInstance) {
            if (value is T) put(key, value)
        }
    }
}