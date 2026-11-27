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

import com.deezer.caupain.config.model.Configuration.OutputType
import com.deezer.caupain.model.gradle.GradleStabilityLevel
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActionInputsTest {

    private fun readInputs(vararg inputs: Pair<String, String>): ActionInputs {
        return ActionEnvironment(inputs.associate { (name, value) -> "INPUT_$name" to value })
            .readInputs()
    }

    @Test
    fun testDefaults() {
        val inputs = readInputs()
        assertNull(inputs.configFile)
        assertEquals(emptyList(), inputs.overrides.versionCatalogPaths)
        assertEquals(emptySet(), inputs.overrides.excludedKeys)
        assertNull(inputs.overrides.policies)
        assertNull(inputs.overrides.cacheDir)
        assertFalse(inputs.overrides.disableCache)
        assertFalse(inputs.overrides.cleanCache)
        assertNull(inputs.overrides.gradleStabilityLevel)
        assertNull(inputs.overrides.searchReleaseNote)
        assertNull(inputs.overrides.githubToken)
        assertFalse(inputs.overrides.verifyExistence)
        assertNull(inputs.gradleWrapperPropertiesPath)
        assertEquals(emptyList(), inputs.outputTypes)
        assertNull(inputs.outputDir)
        assertNull(inputs.outputBaseName)
        assertFalse(inputs.showVersionReferences)
        assertTrue(inputs.jobSummary)
        assertFalse(inputs.failOnUpdates)
    }

    @Test
    fun testAllInputs() {
        val inputs = readInputs(
            "CONFIG_FILE" to "config/caupain.toml",
            "VERSION_CATALOG" to "a.toml\nb.toml",
            "EXCLUDED" to "one\ntwo\none",
            "POLICY" to "first, second",
            "CACHE_DIR" to "cache",
            "NO_CACHE" to "true",
            "CLEAN_CACHE" to "true",
            "GRADLE_STABILITY_LEVEL" to "release-nightly",
            "SEARCH_RELEASE_NOTES" to "true",
            "GITHUB_TOKEN" to "token",
            "VERIFY_EXISTENCE" to "true",
            "GRADLE_WRAPPER_PROPERTIES" to "wrapper.properties",
            "OUTPUT_TYPES" to "console\nMarkdown\nhtml\njson\nhtml",
            "OUTPUT_DIR" to "reports",
            "OUTPUT_BASE_NAME" to "updates",
            "SHOW_VERSION_REFERENCES" to "true",
            "JOB_SUMMARY" to "false",
            "FAIL_ON_UPDATES" to "true",
        )
        assertEquals("config/caupain.toml".toPath(), inputs.configFile)
        assertEquals(listOf("a.toml".toPath(), "b.toml".toPath()), inputs.overrides.versionCatalogPaths)
        assertEquals(setOf("one", "two"), inputs.overrides.excludedKeys)
        assertEquals(listOf("first", "second"), inputs.overrides.policies)
        assertEquals("cache".toPath(), inputs.overrides.cacheDir)
        assertTrue(inputs.overrides.disableCache)
        assertTrue(inputs.overrides.cleanCache)
        assertEquals(GradleStabilityLevel.RELEASE_NIGHTLY, inputs.overrides.gradleStabilityLevel)
        assertEquals(true, inputs.overrides.searchReleaseNote)
        assertEquals("token", inputs.overrides.githubToken)
        assertTrue(inputs.overrides.verifyExistence)
        assertEquals("wrapper.properties".toPath(), inputs.gradleWrapperPropertiesPath)
        assertEquals(
            listOf(OutputType.CONSOLE, OutputType.MARKDOWN, OutputType.HTML, OutputType.JSON),
            inputs.outputTypes
        )
        assertEquals("reports".toPath(), inputs.outputDir)
        assertEquals("updates", inputs.outputBaseName)
        assertTrue(inputs.showVersionReferences)
        assertFalse(inputs.jobSummary)
        assertTrue(inputs.failOnUpdates)
    }

    @Test
    fun testInvalidStabilityLevel() {
        val error = assertFailsWith<InvalidInputException> {
            readInputs("GRADLE_STABILITY_LEVEL" to "unstable")
        }
        assertTrue(error.message.orEmpty().contains("gradle-stability-level"))
        // Valid values are listed in the message
        assertTrue(error.message.orEmpty().contains("release-nightly"))
    }

    @Test
    fun testInvalidOutputType() {
        val error = assertFailsWith<InvalidInputException> {
            readInputs("OUTPUT_TYPES" to "console\npdf")
        }
        assertTrue(error.message.orEmpty().contains("pdf"))
    }

    @Test
    fun testInvalidBooleanIsIgnored() {
        val inputs = readInputs("FAIL_ON_UPDATES" to "yes", "JOB_SUMMARY" to "nope")
        assertFalse(inputs.failOnUpdates)
        assertTrue(inputs.jobSummary)
    }
}
