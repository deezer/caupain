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

package com.deezer.caupain.formatting.json

import com.deezer.caupain.formatting.SinkFormatter
import com.deezer.caupain.formatting.model.Input
import com.deezer.caupain.internal.IODispatcher
import com.deezer.caupain.serialization.DefaultJson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.okio.encodeToBufferedSink
import okio.BufferedSink

/**
 * JsonFormatter is a [SinkFormatter] that formats the output as JSON.
 *
 * @param ioDispatcher The coroutine dispatcher to use for IO operations. Default uses IO dispatcher.
 */
@OptIn(ExperimentalSerializationApi::class)
public class JsonFormatter(
    ioDispatcher: CoroutineDispatcher = IODispatcher,
) : SinkFormatter(ioDispatcher) {

    override suspend fun BufferedSink.writeUpdates(input: Input) {
        DefaultJson.encodeToBufferedSink(input, this)
    }
}