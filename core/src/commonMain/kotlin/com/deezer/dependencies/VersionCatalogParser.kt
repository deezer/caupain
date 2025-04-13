package com.deezer.dependencies

import com.deezer.dependencies.model.versionCatalog.VersionCatalog
import com.deezer.dependencies.serialization.DefaultToml
import com.deezer.dependencies.serialization.decodeFromPath
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import okio.Path
import okio.SYSTEM

internal interface VersionCatalogParser {
    suspend fun parseDependencyInfo(): DependencyInfo
}

internal class DefaultVersionCatalogParser(
    private val toml: Toml = DefaultToml,
    private val versionCatalogPath: Path,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : VersionCatalogParser {
    override suspend fun parseDependencyInfo(): DependencyInfo = withContext(ioDispatcher) {
        DependencyInfo(
            versionCatalog = parseVersionCatalog(),
            ignoredDependencyKeys = parseIgnoredDependencies()
        )
    }

    private fun parseVersionCatalog(): VersionCatalog {
        return toml.decodeFromPath(
            path = versionCatalogPath,
            fileSystem = fileSystem
        )
    }

    private fun parseIgnoredDependencies(): Set<String> {
        val ignoredDependencies = mutableSetOf<String>()
        fileSystem.read(versionCatalogPath) {
            var line = readUtf8Line()
            var didFoundIgnore = false
            while (line != null) {
                if (line.startsWith(IGNORE_COMMENT)) {
                    didFoundIgnore = true
                } else if (didFoundIgnore) {
                    didFoundIgnore = false
                    val lineParts = line.split('=')
                    if (lineParts.size == 2) {
                        ignoredDependencies.add(lineParts[0].trim())
                    }
                }
                line = readUtf8Line()
            }
        }
        return ignoredDependencies
    }

    companion object {
        private const val IGNORE_COMMENT = "#ignoreDependencyUpdate"
    }
}

internal data class DependencyInfo(
    val versionCatalog: VersionCatalog,
    val ignoredDependencyKeys: Set<String>
)