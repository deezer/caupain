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

import com.deezer.caupain.formatting.console.ConsoleFormatter
import com.deezer.caupain.formatting.console.ConsolePrinter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.BeforeTest
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ConsoleFormatterTest : AbstractFormatterTest() {

    private lateinit var printer: TestConsolePrinter

    override lateinit var formatter: ConsoleFormatter

    @BeforeTest
    fun setup() {
        printer = TestConsolePrinter()
        formatter = ConsoleFormatter(printer)
    }

    override fun checkEmptyOutput(isMapEmpty: Boolean) {
        assertEquals(listOf(ConsoleFormatter.NO_UPDATES), printer.output)
        assertEquals(emptyList(), printer.error)
    }

    override fun checkStandardOutput() {
        assertEquals(
            listOf(
                ConsoleFormatter.UPDATES_TITLE,
                "Caupain can be updated from version 1.0.0 to version 1.1.0 via plugins, Github releases, Hombrew, apt",
                "Gradle: 1.0 -> 1.1",
                ConsoleFormatter.VERSIONS_TITLE,
                "- deezer: 1.0.0 -> 2.0.0 (1/2 libraries updated, 1/2 plugins updated)",
                ConsoleFormatter.LIBRARY_TITLE,
                "- com.deezer:library: 1.0.0 -> 2.0.0",
                ConsoleFormatter.PLUGIN_TITLE,
                "- com.deezer:plugin: 1.0.0 -> 2.0.0",
                ConsoleFormatter.IGNORED_TITLE,
                "- com.deezer:ignored-library: 1.0.0 -> 2.0.0"
            ),
            printer.output
        )
        assertEquals(emptyList(), printer.error)
    }

    private class TestConsolePrinter : ConsolePrinter {
        val output = mutableListOf<String>()
        val error = mutableListOf<String>()

        override fun print(message: String) {
            output.add(message)
        }

        override fun printError(message: String) {
            error.add(message)
        }
    }
}