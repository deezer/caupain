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

package com.deezer.caupain.action

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ActionLoggerTest {

    private val lines = mutableListOf<String>()

    private val logger = ActionLogger { lines += it }

    @Test
    fun testLevels() {
        logger.debug("debug")
        logger.info("info")
        logger.lifecycle("lifecycle")
        logger.warn("warn")
        logger.error("error")
        assertEquals(
            listOf(
                "::debug::debug",
                "::debug::info",
                "lifecycle",
                "::warning::warn",
                "::error::error",
            ),
            lines
        )
    }

    @Test
    fun testThrowableStackTraceIsDebugOnly() {
        logger.error("failed", IllegalStateException("boom"))
        assertEquals("::error::failed", lines.first())
        assertEquals(2, lines.size)
        assertTrue(lines[1].startsWith("::debug::"))
        assertTrue(lines[1].contains("boom"))
    }

    @Test
    fun testEscaping() {
        logger.error("100% wrong\nsecond line\r")
        assertEquals(listOf("::error::100%25 wrong%0Asecond line%0D"), lines)
    }

    @Test
    fun testMask() {
        logger.mask("secret")
        assertEquals(listOf("::add-mask::secret"), lines)
    }
}
