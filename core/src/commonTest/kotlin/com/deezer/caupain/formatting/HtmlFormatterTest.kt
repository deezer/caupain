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
        th,
        td {
          border: 1px solid rgb(160 160 160);
          padding: 8px 10px;
        }
        
        td {
          text-align: center;
        }
        
        tr:nth-of-type(even) {
          background-color: #eee;
        }
        
        table {
          border-collapse: collapse;
          border: 2px solid rgb(140 140 140);
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
        th,
        td {
          border: 1px solid rgb(160 160 160);
          padding: 8px 10px;
        }
        
        td {
          text-align: center;
        }
        
        tr:nth-of-type(even) {
          background-color: #eee;
        }
        
        table {
          border-collapse: collapse;
          border: 2px solid rgb(140 140 140);
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