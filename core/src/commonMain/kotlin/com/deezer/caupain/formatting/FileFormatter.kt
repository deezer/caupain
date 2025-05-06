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

package com.deezer.caupain.formatting

import com.deezer.caupain.model.DependenciesUpdateResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.BufferedSink
import okio.FileSystem
import okio.Path
import okio.SYSTEM

/**
 * Interface for formatters that write to a file.
 *
 * @param path The path to the HTML file to write.
 * @param fileSystem The file system to use for writing the file. Default uses the native file system.
 * @param ioDispatcher The coroutine dispatcher to use for IO operations. Default is [Dispatchers.IO].
 */
public abstract class FileFormatter(
    protected val path: Path,
    protected val fileSystem: FileSystem = FileSystem.SYSTEM,
    protected val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : Formatter {

    /**
     * The path to the file where the formatted output will be written.
     */
    public open val outputPath: String
        get() = fileSystem.canonicalize(path).toString()

    override suspend fun format(updates: DependenciesUpdateResult) {
        path.parent?.let(fileSystem::createDirectories)
        withContext(ioDispatcher) {
            fileSystem.write(path) {
                writeUpdates(updates)
            }
        }
    }

    /**
     * Writes the formatted output to the given [BufferedSink].
     */
    protected abstract suspend fun BufferedSink.writeUpdates(updates: DependenciesUpdateResult)
}