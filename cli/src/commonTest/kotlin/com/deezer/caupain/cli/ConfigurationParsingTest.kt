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

package com.deezer.caupain.cli

import com.deezer.caupain.cli.serialization.DefaultToml
import com.deezer.caupain.model.Configuration
import com.deezer.caupain.model.DefaultRepositories
import com.deezer.caupain.model.LibraryExclusion
import com.deezer.caupain.model.PluginExclusion
import com.deezer.caupain.model.Repository
import com.deezer.caupain.model.buildComponentFilter
import kotlinx.serialization.decodeFromString
import okio.Path.Companion.toPath
import org.intellij.lang.annotations.Language
import kotlin.test.Test
import kotlin.test.assertEquals
import com.deezer.caupain.cli.model.Configuration as ParsedConfiguration

class ConfigurationParsingTest {

    @Test
    fun testParsing() {
        assertEquals(
            expected = Configuration(
                repositories = listOf(
                    DefaultRepositories.mavenCentral,
                    Repository(
                        url = "http://www.example.com/repo",
                        componentFilter = buildComponentFilter {
                            include(group = "com.example", name = "example-lib")
                            include("com.example2.**")
                            exclude("com.other")
                        },
                    )
                ),
                pluginRepositories = listOf(
                    DefaultRepositories.gradlePlugins,
                    Repository(
                        url = "http://www.example.com/plugin"
                    )
                ),
                policy = "stability-level",
                cacheDir = "build/cache/caupain".toPath(),
                excludedKeys = setOf("test"),
                excludedLibraries = listOf(
                    LibraryExclusion("com.example", "example-lib"),
                    LibraryExclusion("com.example2.**")
                ),
                excludedPlugins = listOf(
                    PluginExclusion("com.first"),
                    PluginExclusion("com.second")
                )
            ),
            actual = DefaultToml
                .decodeFromString<ParsedConfiguration>(CONFIGURATION)
                .toConfiguration(Configuration())
        )
    }
}

@Language("TOML")
private val CONFIGURATION = """
pluginRepositories = [ 
    "gradlePluginPortal",
    { url = "http://www.example.com/plugin" }
]
policy = "stability-level"
cacheDir = "build/cache/caupain"
outputType = "markdown"
outputPath = "build/reports/dependency-updates.md"    
excludedKeys = ["test"]
excludedLibraries = [
    { group = "com.example", name = "example-lib" },
    { group = "com.example2.**" }
]
excludedPlugins = [ "com.first", "com.second" ]
[[ repositories ]]
default = "mavenCentral"

[[ repositories ]]
url = "http://www.example.com/repo"
includes = [
    { group = "com.example", name = "example-lib" },
    { group = "com.example2.**" }
]
excludes = [ 
    { group = "com.other" }
]
""".trimIndent()