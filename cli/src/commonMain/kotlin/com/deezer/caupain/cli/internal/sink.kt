@file:Suppress("UnusedImport")

package com.deezer.caupain.cli.internal

import com.deezer.caupain.internal.systemSink
import com.github.ajalt.clikt.completion.CompletionCandidates
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.NullableOption
import com.github.ajalt.clikt.parameters.options.RawOption
import com.github.ajalt.clikt.parameters.options.convert
import okio.Buffer
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.SYSTEM
import okio.Sink
import okio.Timeout

fun RawOption.sink(
    createIfNotExist: Boolean = true,
    truncateExisting: Boolean = false,
    createDirectories: Boolean = createIfNotExist,
    fileSystem: FileSystem = FileSystem.SYSTEM,
): NullableOption<OutputSink, OutputSink> {
    return convert({ localization.fileMetavar() }, CompletionCandidates.Path) { s ->
        convertToSink(
            s = s,
            createIfNotExists = createIfNotExist,
            createDirectories = createDirectories,
            truncateExisting = truncateExisting,
            fileSystem = fileSystem,
            context = context
        ) { fail(it) }
    }
}

private fun convertToSink(
    s: String,
    createIfNotExists: Boolean,
    truncateExisting: Boolean,
    createDirectories: Boolean,
    fileSystem: FileSystem,
    context: Context,
    fail: (String) -> Unit,
): OutputSink {
    return if (s == "-") {
        OutputSink.System
    } else {
        val path = convertToPath(
            stringPath = s,
            mustExist = !createIfNotExists,
            canBeFile = true,
            canBeFolder = false,
            canBeSymlink = true,
            fileSystem = fileSystem,
            context = context,
            fail = fail,
        )
        if (createDirectories) {
            path.parent?.let(fileSystem::createDirectories)
        }
        if (!createIfNotExists && !fileSystem.exists(path)) {
            throw IOException("File does not exist: $path")
        }
        OutputSink.File(
            delegate = if (truncateExisting) {
                fileSystem.sink(path)
            } else {
                fileSystem.appendingSink(path)
            },
            path = path,
        )
    }
}

sealed interface OutputSink : Sink {

    data object System : OutputSink {
        private val delegate by lazy { systemSink() }

        override fun write(source: Buffer, byteCount: Long) {
            delegate.write(source, byteCount)
        }

        override fun flush() {
            delegate.flush()
        }

        override fun timeout(): Timeout = delegate.timeout()

        override fun close() {
            flush()
        }
    }

    class File(delegate: Sink, val path: Path) : OutputSink, Sink by delegate {
        override fun toString(): String {
            return "File(path=$path)"
        }
    }
}