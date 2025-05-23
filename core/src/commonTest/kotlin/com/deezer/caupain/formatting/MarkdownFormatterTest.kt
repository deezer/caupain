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

import com.deezer.caupain.formatting.markdown.MarkdownFormatter
import com.deezer.caupain.model.DependenciesUpdateResult
import com.deezer.caupain.model.GradleUpdateInfo
import com.deezer.caupain.model.UpdateInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.intellij.lang.annotations.Language
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MarkdownFormatterTest {

    private lateinit var fileSystem: FakeFileSystem

    private val path = "output.md".toPath()

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var formatter: MarkdownFormatter

    @BeforeTest
    fun setup() {
        fileSystem = FakeFileSystem()
        formatter = MarkdownFormatter(
            path = path,
            fileSystem = fileSystem,
            ioDispatcher = testDispatcher
        )
    }

    private fun assertResult(result: String) {
        fileSystem.read(path) {
            assertEquals(result.trim(), readUtf8().trim())
        }
    }

    @Test
    fun testEmpty() = runTest(testDispatcher) {
        val updates = DependenciesUpdateResult(null, emptyMap())
        formatter.format(updates)
        assertResult(EMPTY_RESULT)
    }

    @Test
    fun testFormat() = runTest(testDispatcher) {
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
        assertResult(FULL_RESULT)
    }

    @AfterTest
    fun teardown() {
        fileSystem.checkNoOpenFiles()
        fileSystem.close()
    }
}

@Language("Markdown")
private const val EMPTY_RESULT = "# No updates available."

@Language("Markdown")
private val FULL_RESULT = """
# Dependency updates
## Gradle
Gradle current version is 1.0 whereas last version is 1.1. See [https://docs.gradle.org/1.1/release-notes.html](https://docs.gradle.org/1.1/release-notes.html).
## Libraries
| Id                 | Name | Current version | Updated version | URL |
| ------------------ | ---- | --------------- | --------------- | --- |
| com.deezer:library |      | 1.0.0           | 2.0.0           |     |
## Plugins
| Id                | Name | Current version | Updated version | URL |
| ----------------- | ---- | --------------- | --------------- | --- |
| com.deezer:plugin |      | 1.0.0           | 2.0.0           |     |
""".trimIndent()