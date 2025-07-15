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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import okio.FileSystem
import okio.Path
import org.intellij.lang.annotations.Language

@OptIn(ExperimentalCoroutinesApi::class)
class JsonFormatterTest : FileFormatterTest() {

    override val extension: String = "json"

    override val emptyResult: String
        get() = EMPTY_RESULT

    override val emptyResultForNonEmptyMap: String
        get() = EMPTY_RESULT_NO_EMPTY_MAP

    override val fullResult: String
        get() = FULL_RESULT

    override fun createFormatter(
        fileSystem: FileSystem,
        path: Path,
        ioDispatcher: CoroutineDispatcher
    ): FileFormatter = JsonFormatter(path, fileSystem, ioDispatcher)

    companion object {
        @Language("JSON")
        private val EMPTY_RESULT = """
            {
                "gradleUpdateInfo": null,
                "updateInfos": {},
                "ignoredUpdateInfos": [],
                "versionReferenceInfo": null,
                "selfUpdateInfo": null
            }
        """.trimIndent()

        @Language("JSON")
        private val EMPTY_RESULT_NO_EMPTY_MAP = """
            {
                "gradleUpdateInfo": null,
                "updateInfos": {
                    "libraries": [],
                    "plugins": []
                },
                "ignoredUpdateInfos": [],
                "versionReferenceInfo": null,
                "selfUpdateInfo": null
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
                "ignoredUpdateInfos": [
                    {
                        "dependency": "ignored-library",
                        "dependencyId": "com.deezer:ignored-library",
                        "name": null,
                        "url": null,
                        "currentVersion": "1.0.0",
                        "updatedVersion": "2.0.0"
                    }
                ],
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
                ],
                "selfUpdateInfo": {
                    "currentVersion": "1.0.0",
                    "updatedVersion": "1.1.0",
                    "sources": [
                        "plugins",
                        "githubReleases",
                        "brew",
                        "apt"
                    ]
                }
            }
        """.trimIndent()
    }
}