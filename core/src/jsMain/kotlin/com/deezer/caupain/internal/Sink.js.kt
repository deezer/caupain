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

package com.deezer.caupain.internal

import okio.Buffer
import okio.IOException
import okio.Sink
import okio.Timeout

private object SystemOutputSink : Sink {

    override fun write(source: Buffer, byteCount: Long) {
        require(byteCount >= 0L) { "byteCount < 0: $byteCount" }
        require(source.size >= byteCount) { "source.size=${source.size} < byteCount=$byteCount" }

        val data = source.readByteArray(byteCount)
        val writtenByteCount = fs.writeSync(process.stdout.fd, data) as Double
        if (writtenByteCount.toLong() != byteCount) {
            throw IOException("expected $byteCount but was $writtenByteCount")
        }
    }

    override fun flush() {
        // No-op
    }

    override fun timeout(): Timeout {
        return Timeout.NONE
    }

    override fun close() {
        // No-op
    }
}

private val process: dynamic
    get() {
        return try {
            js("require('process')")
        } catch (_: Throwable) {
            null
        }
    }

private val fs: dynamic
    get() {
        return try {
            js("require('fs')")
        } catch (_: Throwable) {
            null
        }
    }

/**
 * A platform-specific sink writing to the system standard output. This uses `process.stdout`.
 */
public actual fun systemSink(): Sink = SystemOutputSink
