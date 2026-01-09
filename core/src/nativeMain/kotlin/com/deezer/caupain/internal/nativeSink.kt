@file:OptIn(ExperimentalForeignApi::class)

package com.deezer.caupain.internal

import kotlinx.cinterop.ByteVarOf
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.usePinned
import okio.Buffer
import okio.Buffer.UnsafeCursor
import okio.FileNotFoundException
import okio.IOException
import okio.Sink
import okio.Timeout
import platform.posix.ENOENT
import platform.posix.errno
import platform.posix.fflush
import platform.posix.fwrite
import platform.posix.stdout
import platform.posix.strerror

private object SystemOutputSink : Sink {

    private val unsafeCursor = UnsafeCursor()

    override fun write(source: Buffer, byteCount: Long) {
        require(byteCount >= 0L) { "byteCount < 0: $byteCount" }
        require(source.size >= byteCount) { "source.size=${source.size} < byteCount=$byteCount" }

        var byteCount = byteCount
        while (byteCount > 0) {
            // Get the first segment, which we will read a contiguous range of bytes from.
            val cursor = source.readUnsafe(unsafeCursor)
            val segmentReadableByteCount = cursor.next()
            val attemptCount = minOf(byteCount, segmentReadableByteCount.toLong()).toInt()

            // Copy bytes from that segment into the file.
            val bytesWritten = cursor.data!!.usePinned { pinned ->
                fwrite(
                    pinned.addressOf(cursor.start),
                    1u,
                    attemptCount.toUInt().convert(),
                    stdout
                ).toLong()
            }

            // Consume the bytes from the segment.
            cursor.close()
            source.skip(bytesWritten)
            byteCount -= bytesWritten

            // If the write was shorter than expected, some I/O failed.
            if (bytesWritten < attemptCount) {
                throw errnoToIOException(errno)
            }
        }
    }

    override fun flush() {
        if (fflush(stdout) != 0) throw errnoToIOException(errno)
    }

    override fun timeout(): Timeout = Timeout.NONE

    override fun close() {
        flush()
    }

    private fun errnoToIOException(errno: Int): IOException {
        val message = strerror(errno)
        val messageString = if (message != null) {
            Buffer().writeNullTerminated(message).readUtf8()
        } else {
            "errno: $errno"
        }
        return if (errno == ENOENT) {
            FileNotFoundException(messageString)
        } else {
            IOException(messageString)
        }
    }
}

private fun Buffer.writeNullTerminated(bytes: CPointer<ByteVarOf<Byte>>): Buffer = apply {
    var pos = 0
    while (true) {
        val byte = bytes[pos++].toInt()
        if (byte == 0) {
            break
        } else {
            writeByte(byte)
        }
    }
}

/**
 * A platform-specific sink writing to the system standard output. This uses [stdout].
 */
public actual fun systemSink(): Sink = SystemOutputSink