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

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
abstract class FileFormatterTest : AbstractFormatterTest() {

    private lateinit var fileSystem: FakeFileSystem

    protected abstract val extension: String

    private val path by lazy { "output.$extension".toPath() }

    override lateinit var formatter: FileFormatter

    protected abstract val emptyResult: String

    protected open val emptyResultForNonEmptyMap: String
        get() = emptyResult

    protected abstract val fullResult: String

    @BeforeTest
    fun setup() {
        fileSystem = FakeFileSystem()
        formatter = createFormatter(fileSystem, path, testDispatcher)
    }

    protected abstract fun createFormatter(
        fileSystem: FileSystem,
        path: Path,
        ioDispatcher: CoroutineDispatcher
    ): FileFormatter

    private fun assertResult(result: String) {
        fileSystem.read(path) {
            assertEquals(result.trim(), readUtf8().trim())
        }
    }

    override fun checkEmptyOutput(isMapEmpty: Boolean) {
        assertResult(if (isMapEmpty) emptyResult else emptyResultForNonEmptyMap)
    }

    override fun checkStandardOutput() {
        assertResult(fullResult)
    }

    @AfterTest
    fun teardown() {
        fileSystem.checkNoOpenFiles()
        fileSystem.close()
    }
}