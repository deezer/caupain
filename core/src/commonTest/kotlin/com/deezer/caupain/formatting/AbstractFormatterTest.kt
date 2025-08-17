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

import com.deezer.caupain.formatting.model.Input
import com.deezer.caupain.formatting.model.VersionReferenceInfo
import com.deezer.caupain.model.GradleUpdateInfo
import com.deezer.caupain.model.SelfUpdateInfo
import com.deezer.caupain.model.UpdateInfo
import com.deezer.caupain.toSimpleVersion
import com.deezer.caupain.toStaticVersion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
abstract class AbstractFormatterTest {

    protected val testDispatcher = UnconfinedTestDispatcher()

    protected abstract val formatter: Formatter

    @Test
    fun testEmpty() = runTest(testDispatcher) {
        formatter.format(Input(null, emptyMap(), emptyList(), null, null))
        checkEmptyOutput(isMapEmpty = true)
    }

    @Test
    fun testEmptyWithNonEmptyMap() = runTest(testDispatcher) {
        formatter.format(
            Input(
                gradleUpdateInfo = null,
                updateInfos = mapOf(
                    UpdateInfo.Type.LIBRARY to emptyList(),
                    UpdateInfo.Type.PLUGIN to emptyList()
                ),
                ignoredUpdateInfos = emptyList(),
                versionReferenceInfo = null,
                selfUpdateInfo = null
            )
        )
        checkEmptyOutput(isMapEmpty = false)
    }

    protected abstract fun checkEmptyOutput(isMapEmpty: Boolean)

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
                        "http://www.example.com/library",
                        "http://www.example.com/library/releases",
                        "1.0.0".toSimpleVersion(),
                        "2.0.0".toStaticVersion()
                    )
                ),
                UpdateInfo.Type.PLUGIN to listOf(
                    UpdateInfo(
                        "plugin",
                        "com.deezer:plugin",
                        null,
                        "http://www.example.com/plugin",
                        "http://www.example.com/plugin/releases",
                        "1.0.0".toSimpleVersion(),
                        "2.0.0".toStaticVersion()
                    )
                )
            ),
            ignoredUpdateInfos = listOf(
                UpdateInfo(
                    "ignored-library",
                    "com.deezer:ignored-library",
                    null,
                    null,
                    releaseNoteUrl = null,
                    "1.0.0".toSimpleVersion(),
                    "2.0.0".toStaticVersion()
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
            ),
            selfUpdateInfo = SelfUpdateInfo(
                currentVersion = "1.0.0",
                updatedVersion = "1.1.0",
                sources = SelfUpdateInfo.Source.entries
            )
        )
        formatter.format(updates)
        checkStandardOutput()
    }

    protected abstract fun checkStandardOutput()
}