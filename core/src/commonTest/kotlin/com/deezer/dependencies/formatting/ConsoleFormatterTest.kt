package com.deezer.dependencies.formatting

import com.deezer.dependencies.formatting.console.ConsoleFormatter
import com.deezer.dependencies.formatting.console.ConsolePrinter
import com.deezer.dependencies.model.UpdateInfo
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
        val updates = emptyMap<UpdateInfo.Type, List<UpdateInfo>>()
        formatter.format(updates)
        advanceUntilIdle()
        assertEquals(listOf(ConsoleFormatter.NO_UPDATES), printer.output)
        assertEquals(emptyList(), printer.error)
    }

    @Test
    fun testFormat() = runTest {
        val updates = mapOf(
            UpdateInfo.Type.LIBRARY to listOf(
                UpdateInfo("library", "com.deezer:library", null, null, "1.0.0", "2.0.0")
            ),
            UpdateInfo.Type.PLUGIN to listOf(
                UpdateInfo("plugin", "com.deezer:plugin", null, null, "1.0.0", "2.0.0")
            )
        )
        formatter.format(updates)
        advanceUntilIdle()
        assertEquals(
            listOf(
                ConsoleFormatter.UPDATES_TITLE,
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