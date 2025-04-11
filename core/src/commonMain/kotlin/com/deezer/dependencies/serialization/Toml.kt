package com.deezer.dependencies.serialization

import net.peanuuutz.tomlkt.Toml
import net.peanuuutz.tomlkt.TomlReader
import net.peanuuutz.tomlkt.decodeFromReader
import okio.BufferedSource
import okio.EOFException
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import okio.buffer
import okio.use

class TomlOkioReader(private val source: BufferedSource) : TomlReader {

    override fun read(): Int = try {
        source.readUtf8CodePoint()
    } catch (e: EOFException) {
        -1
    }
}

val DefaultToml: Toml by lazy {
    Toml {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
}

inline fun <reified T> Toml.decodeFromPath(
    path: Path,
    fileSystem: FileSystem = FileSystem.SYSTEM
): T {
    return fileSystem
        .source(path)
        .buffer()
        .use { decodeFromReader(TomlOkioReader(it)) }
}