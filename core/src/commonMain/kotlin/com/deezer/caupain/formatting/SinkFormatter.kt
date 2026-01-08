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

import com.deezer.caupain.formatting.model.Input
import com.deezer.caupain.internal.IODispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okio.BufferedSink
import okio.Sink
import okio.buffer
import okio.use

/**
 * Interface for formatters that write to a [Sink].
 *
 * @property ioDispatcher The coroutine dispatcher to use for IO operations. Default is IO dispatcher.
 */
public abstract class SinkFormatter(
    protected val ioDispatcher: CoroutineDispatcher = IODispatcher,
) {

    /**
     * Formats the update result to the desired output format.
     */
    public suspend fun format(input: Input, sink: Sink) {
        withContext(ioDispatcher) {
            sink.buffer().use { it.writeUpdates(input) }
        }
    }

    /**
     * Writes the formatted output to the given [BufferedSink].
     */
    protected abstract suspend fun BufferedSink.writeUpdates(input: Input)
}