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

import okio.Path.Companion.toPath
import okio.buffer
import okio.fakefilesystem.FakeFileSystem
import okio.use
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkflowFilesTest {

    private lateinit var fileSystem: FakeFileSystem

    private val outputPath = "output.txt".toPath()

    private val summaryPath = "summary.md".toPath()

    @BeforeTest
    fun setup() {
        fileSystem = FakeFileSystem()
    }

    @AfterTest
    fun teardown() {
        fileSystem.checkNoOpenFiles()
        fileSystem.close()
    }

    private fun workflowFiles(vararg variables: Pair<String, String>) =
        WorkflowFiles(fileSystem, ActionEnvironment(mapOf(*variables)))

    @Test
    fun testSetOutput() {
        val files = workflowFiles("GITHUB_OUTPUT" to outputPath.toString())
        files.setOutput("has-updates", "true")
        files.setOutput("report-paths", "first\nsecond")

        val lines = fileSystem.read(outputPath) { readUtf8() }.lines()
        // name<<delimiter, value lines, delimiter, for each output, then an empty trailing line
        assertEquals(8, lines.size)
        val firstDelimiter = lines[0].removePrefix("has-updates<<")
        assertTrue(firstDelimiter.isNotEmpty())
        assertEquals("true", lines[1])
        assertEquals(firstDelimiter, lines[2])
        val secondDelimiter = lines[3].removePrefix("report-paths<<")
        assertTrue(secondDelimiter.isNotEmpty())
        assertEquals("first", lines[4])
        assertEquals("second", lines[5])
        assertEquals(secondDelimiter, lines[6])
        assertEquals("", lines[7])
    }

    @Test
    fun testSetOutputUsesDifferentDelimiters() {
        val files = workflowFiles("GITHUB_OUTPUT" to outputPath.toString())
        files.setOutput("a", "1")
        files.setOutput("b", "2")
        val delimiters = fileSystem.read(outputPath) { readUtf8() }
            .lines()
            .filter { it.contains("<<") }
            .map { it.substringAfter("<<") }
        assertEquals(2, delimiters.size)
        assertFalse(delimiters[0] == delimiters[1])
    }

    @Test
    fun testSetOutputAppends() {
        fileSystem.write(outputPath) { writeUtf8("existing=value\n") }
        workflowFiles("GITHUB_OUTPUT" to outputPath.toString()).setOutput("name", "value")
        val content = fileSystem.read(outputPath) { readUtf8() }
        assertTrue(content.startsWith("existing=value\n"))
        assertTrue(content.contains("name<<"))
    }

    @Test
    fun testSetOutputWithoutFile() {
        workflowFiles().setOutput("name", "value")
        assertFalse(fileSystem.exists(outputPath))
    }

    @Test
    fun testSummary() {
        fileSystem.write(summaryPath) { writeUtf8("# Existing\n") }
        val files = workflowFiles("GITHUB_STEP_SUMMARY" to summaryPath.toString())
        assertTrue(files.hasSummary)
        files.openSummarySink()!!.buffer().use { it.writeUtf8("# New\n") }
        assertEquals("# Existing\n# New\n", fileSystem.read(summaryPath) { readUtf8() })
    }

    @Test
    fun testNoSummary() {
        val files = workflowFiles()
        assertFalse(files.hasSummary)
        assertNull(files.openSummarySink())
    }
}
