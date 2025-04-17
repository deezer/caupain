package com.deezer.dependencies.internal

import okio.HashingSink
import okio.blackholeSink
import okio.buffer
import okio.use

internal fun String.toSHA256Hex(): String = HashingSink
    .sha256(blackholeSink())
    .apply {
        buffer().use { it.writeUtf8(this@toSHA256Hex) }
    }
    .hash
    .hex()