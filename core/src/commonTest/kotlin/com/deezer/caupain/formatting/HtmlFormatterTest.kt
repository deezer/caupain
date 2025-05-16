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

import com.deezer.caupain.formatting.html.HtmlFormatter
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
class HtmlFormatterTest {

    private lateinit var fileSystem: FakeFileSystem

    private val path = "output.html".toPath()

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var formatter: HtmlFormatter

    @BeforeTest
    fun setup() {
        fileSystem = FakeFileSystem()
        formatter = HtmlFormatter(
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

@Language("HTML")
private const val EMPTY_RESULT = """
<html>
  <head>
    <style>
        body {
          background-color: Canvas;
          color: CanvasText;
          color-scheme: light dark;
        }
            
        th,
        td {
          border: 1px solid ButtonBorder;
          padding: 8px 10px;
        }
        
        td {
          text-align: center;
        }
        
        tr:nth-of-type(even) {
          background-color: ButtonFace;
        }
        
        table {
          border-collapse: collapse;
          border: 2px solid ButtonBorder;
          width: 100%;
        }  
        </style>
  </head>
  <body>
    <h1>No updates available.</h1>
  </body>
</html>    
"""

@Language("HTML")
private const val FULL_RESULT = """
<html>
  <head>
    <style>
        body {
          background-color: Canvas;
          color: CanvasText;
          color-scheme: light dark;
        }
            
        th,
        td {
          border: 1px solid ButtonBorder;
          padding: 8px 10px;
        }
        
        td {
          text-align: center;
        }
        
        tr:nth-of-type(even) {
          background-color: ButtonFace;
        }
        
        table {
          border-collapse: collapse;
          border: 2px solid ButtonBorder;
          width: 100%;
        }  
        </style>
  </head>
  <body>
    <h1>Dependency updates</h1>
    <h2>Gradle</h2>
    <p>Gradle current version is 1.0 whereas last version is 1.1. See <a href="https://docs.gradle.org/1.1/release-notes.html">release note</a>.</p>
    <h2>Libraries</h2>
    <p>
      <table>
        <tr>
          <th>Id</th>
          <th>Name</th>
          <th>Current version</th>
          <th>Updated version</th>
          <th>URL</th>
        </tr>
        <tr>
          <td>com.deezer:library</td>
          <td></td>
          <td>1.0.0</td>
          <td>2.0.0</td>
          <td></td>
        </tr>
      </table>
    </p>
    <h2>Plugins</h2>
    <p>
      <table>
        <tr>
          <th>Id</th>
          <th>Name</th>
          <th>Current version</th>
          <th>Updated version</th>
          <th>URL</th>
        </tr>
        <tr>
          <td>com.deezer:plugin</td>
          <td></td>
          <td>1.0.0</td>
          <td>2.0.0</td>
          <td></td>
        </tr>
      </table>
    </p>
  </body>
</html>    
"""