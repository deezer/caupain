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

import com.deezer.caupain.formatting.json.JsonFormatter
import com.deezer.caupain.formatting.model.Input
import com.deezer.caupain.formatting.model.VersionReferenceInfo
import com.deezer.caupain.model.GradleUpdateInfo
import com.deezer.caupain.model.UpdateInfo
import com.deezer.caupain.toSimpleVersion
import com.deezer.caupain.toStaticVersion
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
class JsonFormatterTest {

    private lateinit var fileSystem: FakeFileSystem

    private val path = "output.json".toPath()

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var formatter: JsonFormatter

    @BeforeTest
    fun setup() {
        fileSystem = FakeFileSystem()
        formatter = JsonFormatter(
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
        val updates = Input(null, emptyMap(), null)
        formatter.format(updates)
        assertResult(EMPTY_RESULT)
    }

    @Test
    fun testFormat() = runTest(testDispatcher) {
        val updates = Input(
            gradleUpdateInfo = GradleUpdateInfo("1.0", "1.1"),
            updateInfos = mapOf(
                UpdateInfo.Type.LIBRARY to listOf(
                    UpdateInfo(
                        "library",
                        "com.deezer:library",
                        null,
                        null,
                        "1.0.0".toSimpleVersion(),
                        "2.0.0".toStaticVersion()
                    )
                ),
                UpdateInfo.Type.PLUGIN to listOf(
                    UpdateInfo(
                        "plugin",
                        "com.deezer:plugin",
                        null,
                        null,
                        "1.0.0".toSimpleVersion(),
                        "2.0.0".toStaticVersion()
                    )
                )
            ),
            versionReferenceInfo = listOf(
                VersionReferenceInfo(
                    id = "deezer",
                    libraryKeys = listOf("library", "other-library"),
                    updatedLibraries = mapOf("library" to "2.0.0".toStaticVersion()),
                    pluginKeys = listOf("plugin"),
                    updatedPlugins = mapOf("plugin" to "2.0.0".toStaticVersion()),
                    currentVersion = "1.0.0".toSimpleVersion(),
                    updatedVersion = "2.0.0".toStaticVersion(),
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

    companion object {
        @Language("JSON")
        private val EMPTY_RESULT = """
            {
                "gradleUpdateInfo": null,
                "updateInfos": {},
                "versionReferenceInfo": null
            }
        """.trimIndent()

        @Language("JSON")
        private val FULL_RESULT = """
            {
                "gradleUpdateInfo": {
                    "currentVersion": "1.0",
                    "updatedVersion": "1.1"
                },
                "updateInfos": {
                    "libraries": [
                        {
                            "dependency": "library",
                            "dependencyId": "com.deezer:library",
                            "name": null,
                            "url": null,
                            "currentVersion": "1.0.0",
                            "updatedVersion": "2.0.0"
                        }
                    ],
                    "plugins": [
                        {
                            "dependency": "plugin",
                            "dependencyId": "com.deezer:plugin",
                            "name": null,
                            "url": null,
                            "currentVersion": "1.0.0",
                            "updatedVersion": "2.0.0"
                        }
                    ]
                },
                "versionReferenceInfo": [
                    {
                        "id": "deezer",
                        "libraryKeys": [
                            "library",
                            "other-library"
                        ],
                        "updatedLibraries": {
                            "library": "2.0.0"
                        },
                        "pluginKeys": [
                            "plugin"
                        ],
                        "updatedPlugins": {
                            "plugin": "2.0.0"
                        },
                        "currentVersion": "1.0.0",
                        "updatedVersion": "2.0.0"
                    }
                ]
            }
        """.trimIndent()
    }
}