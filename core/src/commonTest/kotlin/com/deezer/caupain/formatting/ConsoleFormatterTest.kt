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
import com.deezer.caupain.model.DependenciesUpdateResult
import com.deezer.caupain.model.GradleUpdateInfo
import com.deezer.caupain.model.UpdateInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ConsoleFormatterTest {

    private lateinit var printer: TestConsolePrinter

    private lateinit var formatter: ConsoleFormatter

    @BeforeTest
    fun setup() {
        printer = TestConsolePrinter()
        formatter = ConsoleFormatter(printer)
    }

    @Test
    fun testEmpty() = runTest {
        val updates = DependenciesUpdateResult(null, emptyMap())
        formatter.format(updates)
        advanceUntilIdle()
        assertEquals(listOf(ConsoleFormatter.NO_UPDATES), printer.output)
        assertEquals(emptyList(), printer.error)
    }

    @Test
    fun testFormat() = runTest {
        val updates = DependenciesUpdateResult(
            gradleUpdateInfo = GradleUpdateInfo("1.0", "1.1"),
            updateInfos = mapOf(
                UpdateInfo.Type.LIBRARY to listOf(
                    UpdateInfo("library", "com.deezer:library", null, null, "1.0.0", "2.0.0")
                ),
                UpdateInfo.Type.PLUGIN to listOf(
                    UpdateInfo("plugin", "com.deezer:plugin", null, null, "1.0.0", "2.0.0")
                )
            )
        )
        formatter.format(updates)
        advanceUntilIdle()
        assertEquals(
            listOf(
                ConsoleFormatter.UPDATES_TITLE,
                "Gradle: 1.0 -> 1.1",
                ConsoleFormatter.LIBRARY_TITLE,
                "- com.deezer:library: 1.0.0 -> 2.0.0",
                ConsoleFormatter.PLUGIN_TITLE,
                "- com.deezer:plugin: 1.0.0 -> 2.0.0"
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