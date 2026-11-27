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

package com.deezer.caupain.config

import com.deezer.caupain.config.model.Configuration
import com.deezer.caupain.config.model.Configuration.OutputType
import com.deezer.caupain.config.serialization.DefaultToml
import kotlinx.serialization.decodeFromString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class OutputTypeTest {

    @Test
    fun testKeysAndExtensions() {
        assertEquals(
            listOf(
                Triple(OutputType.CONSOLE, "console", null),
                Triple(OutputType.HTML, "html", "html"),
                Triple(OutputType.MARKDOWN, "markdown", "md"),
                Triple(OutputType.JSON, "json", "json"),
            ),
            OutputType.entries.map { Triple(it, it.key, it.fileExtension) }
        )
    }

    @Test
    fun testFromKey() {
        for (type in OutputType.entries) {
            assertEquals(type, OutputType.fromKey(type.key))
        }
    }

    @Test
    fun testFromKeyIgnoresCase() {
        assertEquals(OutputType.MARKDOWN, OutputType.fromKey("Markdown"))
    }

    @Test
    fun testFromUnknownKey() {
        assertNull(OutputType.fromKey("pdf"))
        assertNull(OutputType.fromKey("md"))
    }

    @Test
    fun testParsingFromConfigurationFile() {
        val configuration = DefaultToml.decodeFromString<Configuration>(
            """outputTypes = ["console", "Markdown", "JSON"]"""
        )
        assertEquals(
            setOf(OutputType.CONSOLE, OutputType.MARKDOWN, OutputType.JSON),
            configuration.outputTypes
        )
    }

    @Test
    fun testParsingUnknownOutputTypeFromConfigurationFile() {
        assertFailsWith<IllegalArgumentException> {
            DefaultToml.decodeFromString<Configuration>("""outputType = "pdf"""")
        }
    }
}
