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