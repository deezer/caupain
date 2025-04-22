package com.deezer.caupain.internal

import okio.BufferedSink

internal fun BufferedSink.asAppendable(): Appendable = object : Appendable {
    override fun append(value: Char): Appendable {
        writeUtf8CodePoint(value.code)
        return this
    }

    override fun append(value: CharSequence?): Appendable {
        writeUtf8(value.toString())
        return this
    }

    override fun append(value: CharSequence?, startIndex: Int, endIndex: Int): Appendable {
        writeUtf8(value.toString(), startIndex, endIndex)
        return this
    }
}