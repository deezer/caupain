package com.deezer.caupain

import com.deezer.caupain.internal.asAppendable
import okio.Buffer
import okio.use
import kotlin.test.Test
import kotlin.test.assertEquals

class SourceTest {

    @Test
    fun testAppendable() {
        Buffer().use { buffer ->
            buffer
                .asAppendable()
                .append('H')
                .appendLine("elloWorld !")
            assertEquals("HelloWorld !\n", buffer.readUtf8())
        }
    }
}