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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import okio.FileSystem
import okio.Path
import org.intellij.lang.annotations.Language

@OptIn(ExperimentalCoroutinesApi::class)
class HtmlFormatterTest : FileFormatterTest() {

    override val extension: String = "html"

    override val emptyResult: String
        get() = EMPTY_RESULT

    override val fullResult: String
        get() = FULL_RESULT

    override fun createFormatter(ioDispatcher: CoroutineDispatcher) =  HtmlFormatter(ioDispatcher)
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
    <h2>Self Update</h2>
    <p>Caupain current version is 1.0.0 whereas last version is 1.1.0.<br>You can update Caupain via :
      <ul>
        <li>plugins</li>
        <li><a href="https://github.com/deezer/caupain/releases">Github releases</a></li>
        <li>Hombrew</li>
        <li>apt</li>
      </ul>
    </p>
    <h2>Gradle</h2>
    <p>Gradle current version is 1.0 whereas last version is 1.1. See <a href="https://docs.gradle.org/1.1/release-notes.html">release note</a>.</p>
    <h2>Version References</h2>
    <p>
      <table>
        <tr>
          <th>Id</th>
          <th>Current version</th>
          <th>Updated version</th>
          <th>Details</th>
        </tr>
        <tr>
          <td>deezer</td>
          <td>1.0.0</td>
          <td>2.0.0</td>
          <td>Libraries: <a href="#update_LIBRARY_library">library</a><br>Plugins: <a href="#update_PLUGIN_plugin">plugin</a><br>Updates for these dependency using the reference were not found for the updated version:
            <ul>
              <li>other-library: (no update found)</li>
            </ul>
          </td>
        </tr>
      </table>
    </p>
    <h2>Libraries</h2>
    <p>
      <table>
        <tr>
          <th>Id</th>
          <th>Name</th>
          <th>Current version</th>
          <th>Updated version</th>
          <th>URLs</th>
        </tr>
        <tr id="update_LIBRARY_library">
          <td>com.deezer:library</td>
          <td></td>
          <td>1.0.0</td>
          <td>2.0.0</td>
          <td><a href="http://www.example.com/library/releases">Release notes</a><br><a href="http://www.example.com/library">Project</a></td>
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
          <th>URLs</th>
        </tr>
        <tr id="update_PLUGIN_plugin">
          <td>com.deezer:plugin</td>
          <td></td>
          <td>1.0.0</td>
          <td>2.0.0</td>
          <td><a href="http://www.example.com/plugin/releases">Release notes</a><br><a href="http://www.example.com/plugin">Project</a></td>
        </tr>
      </table>
    </p>
    <h2>Ignored</h2>
    <p>
      <table>
        <tr>
          <th>Id</th>
          <th>Current version</th>
          <th>Updated version</th>
        </tr>
        <tr>
          <td>com.deezer:ignored-library</td>
          <td>1.0.0</td>
          <td>2.0.0</td>
        </tr>
      </table>
    </p>
  </body>
</html>    
"""