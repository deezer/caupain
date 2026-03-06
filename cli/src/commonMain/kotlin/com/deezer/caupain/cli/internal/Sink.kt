/*
 * MIT License
 *
 * Copyright (c) 2026 Deezer
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
 *
 */

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
