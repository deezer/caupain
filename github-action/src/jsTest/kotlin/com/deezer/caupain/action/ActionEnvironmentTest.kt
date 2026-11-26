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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ActionEnvironmentTest {

    @Test
    fun testInputWithHyphenInName() {
        val env = ActionEnvironment(mapOf("INPUT_VERSION-CATALOG" to "libs.toml"))
        assertEquals("libs.toml", env.input("version-catalog"))
    }

    @Test
    fun testInputWithUnderscoreInName() {
        val env = ActionEnvironment(mapOf("INPUT_VERSION_CATALOG" to "libs.toml"))
        assertEquals("libs.toml", env.input("version-catalog"))
    }

    @Test
    fun testInputWithSpaceInName() {
        val env = ActionEnvironment(mapOf("INPUT_MY_INPUT" to "value"))
        assertEquals("value", env.input("my input"))
    }

    @Test
    fun testInputIsTrimmed() {
        val env = ActionEnvironment(mapOf("INPUT_NAME" to "  value \n"))
        assertEquals("value", env.input("name"))
    }

    @Test
    fun testMissingOrBlankInputIsNull() {
        val env = ActionEnvironment(mapOf("INPUT_BLANK" to "  ", "INPUT_NULL" to null))
        assertNull(env.input("missing"))
        assertNull(env.input("blank"))
        assertNull(env.input("null"))
    }

    @Test
    fun testBooleanInput() {
        val env = ActionEnvironment(
            mapOf("INPUT_YES" to "true", "INPUT_NO" to "FALSE", "INPUT_MIXED" to " True ")
        )
        assertEquals(true, env.booleanInput("yes"))
        assertEquals(false, env.booleanInput("no"))
        assertEquals(true, env.booleanInput("mixed"))
        assertNull(env.booleanInput("missing"))
    }

    @Test
    fun testInvalidBooleanInputIsNull() {
        val env = ActionEnvironment(mapOf("INPUT_BAD" to "maybe"))
        assertNull(env.booleanInput("bad"))
    }

    @Test
    fun testListInput() {
        val env = ActionEnvironment(mapOf("INPUT_LIST" to "\n  first \n\nsecond\r\nthird\n"))
        assertEquals(listOf("first", "second", "third"), env.listInput("list"))
        assertEquals(emptyList(), env.listInput("missing"))
    }

    @Test
    fun testPathInput() {
        val env = ActionEnvironment(mapOf("INPUT_PATH" to "some/path"))
        assertEquals("some/path".toPath(), env.pathInput("path"))
        assertNull(env.pathInput("missing"))
    }

    @Test
    fun testRunnerFiles() {
        val env = ActionEnvironment(
            mapOf(
                "GITHUB_OUTPUT" to "/out",
                "GITHUB_STEP_SUMMARY" to "/summary",
                "RUNNER_TEMP" to "/tmp",
            )
        )
        assertEquals("/out".toPath(), env.outputFile)
        assertEquals("/summary".toPath(), env.stepSummaryFile)
        assertEquals("/tmp".toPath(), env.runnerTemp)

        val empty = ActionEnvironment(mapOf("GITHUB_OUTPUT" to ""))
        assertNull(empty.outputFile)
        assertNull(empty.stepSummaryFile)
        assertNull(empty.runnerTemp)
    }
}
