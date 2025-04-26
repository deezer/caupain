package com.deezer.caupain

import com.deezer.caupain.model.Ignores
import com.deezer.caupain.model.versionCatalog.VersionCatalog
import com.deezer.caupain.serialization.DefaultToml
import com.deezer.caupain.serialization.decodeFromPath
import com.deezer.caupain.toml.IgnoreParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import okio.Path
import okio.SYSTEM

internal interface VersionCatalogParser {
    suspend fun parseDependencyInfo(): VersionCatalogParseResult
}

internal class DefaultVersionCatalogParser(
    private val toml: Toml = DefaultToml,
    private val versionCatalogPath: Path,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : VersionCatalogParser {

    private val ignoreParser = IgnoreParser(fileSystem, versionCatalogPath, ioDispatcher)

    override suspend fun parseDependencyInfo(): VersionCatalogParseResult =
        withContext(ioDispatcher) {
            VersionCatalogParseResult(
                versionCatalog = toml.decodeFromPath<VersionCatalog>(
                    path = versionCatalogPath,
                    fileSystem = fileSystem
                ),
                ignores = ignoreParser.computeIgnores()
            )
        }
}

internal data class VersionCatalogParseResult(
    val versionCatalog: VersionCatalog,
    val ignores: Ignores
)