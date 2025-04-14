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
    suspend fun parseDependencyInfo(): VersionCatalog
}

internal class DefaultVersionCatalogParser(
    private val toml: Toml = DefaultToml,
    private val versionCatalogPath: Path,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : VersionCatalogParser {
    override suspend fun parseDependencyInfo(): VersionCatalog = withContext(ioDispatcher) {
        toml.decodeFromPath(
            path = versionCatalogPath,
            fileSystem = fileSystem
        )
    }
}