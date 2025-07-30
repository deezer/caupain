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

import com.deezer.caupain.model.VersionCatalogInfo
import com.deezer.caupain.model.versionCatalog.VersionCatalog
import com.deezer.caupain.serialization.DefaultToml
import com.deezer.caupain.serialization.decodeFromPath
import com.deezer.caupain.versionCatalog.SupplementaryParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import okio.Path
import okio.SYSTEM

internal interface VersionCatalogParser {
    suspend fun parseDependencyInfo(versionCatalogPath: Path): VersionCatalogParseResult
}

internal class DefaultVersionCatalogParser(
    private val toml: Toml = DefaultToml,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : VersionCatalogParser {

    private val parser = SupplementaryParser(fileSystem, ioDispatcher)

    override suspend fun parseDependencyInfo(versionCatalogPath: Path): VersionCatalogParseResult =
        withContext(ioDispatcher) {
            VersionCatalogParseResult(
                versionCatalog = toml.decodeFromPath<VersionCatalog>(
                    path = versionCatalogPath,
                    fileSystem = fileSystem
                ),
                info = parser.parse(versionCatalogPath)
            )
        }
}

internal data class VersionCatalogParseResult(
    val versionCatalog: VersionCatalog,
    val info: VersionCatalogInfo
)