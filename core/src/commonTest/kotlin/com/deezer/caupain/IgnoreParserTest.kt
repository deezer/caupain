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

package com.deezer.caupain

import com.deezer.caupain.model.Ignores
import com.deezer.caupain.toml.IgnoreParser
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
class IgnoreParserTest {

    private lateinit var fileSystem: FakeFileSystem

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setup() {
        fileSystem = FakeFileSystem()
    }

    @AfterTest
    fun teardown() {
        fileSystem.checkNoOpenFiles()
        fileSystem.close()
    }

    @Test
    fun testParser() {
        val path = "libs.versions.toml".toPath()
        fileSystem.write(path) { writeUtf8(FILE) }
        runTest(testDispatcher) {
            assertEquals(
                expected = Ignores(
                    refs = setOf("kotlin"),
                    libraryKeys = setOf("kotlin-test"),
                    pluginKeys = setOf("jetbrains-kotlin-jvm")
                ),
                actual = IgnoreParser(fileSystem, testDispatcher).computeIgnores(path)
            )
        }
    }
}

@Language("toml")
private val FILE = """
[versions]
junit = "4.13.2"
kotlin = "2.1.20" #ignoreUpdates
kotlinx-coroutines = "1.10.2"

[libraries]
kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin" } #ignoreUpdates
kotlin-test-junit = { module = "org.jetbrains.kotlin:kotlin-test-junit", version.ref = "kotlin" }

[bundles]
clikt = ["clikt", "clikt-markdown", "mordant", "mordant-coroutines"]
ktor = ["ktor-client-core", "ktor-client-logging", "ktor-client-content-negociation"]

[plugins]
kotlinMultiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlinx-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
jetbrains-kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "jetbrains-kotlin-jvm" } #ignoreUpdates
kotlinx-atomicfu = "org.jetbrains.kotlinx.atomicfu:0.27.0" #wrongcomment
dokka = "org.jetbrains.dokka:2.0.0"
detekt = { id = "io.gitlab.arturbosch.detekt", version.ref = "detekt" }
""".trimIndent()