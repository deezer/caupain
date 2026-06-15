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

import com.deezer.caupain.cli.model.Configuration
import com.deezer.caupain.cli.serialization.DefaultToml
import com.deezer.caupain.model.DefaultRepositories
import com.deezer.caupain.model.Filter
import com.deezer.caupain.model.GradleDependencyVersion
import com.deezer.caupain.model.LibraryExclusion
import com.deezer.caupain.model.LibraryInclusion
import com.deezer.caupain.model.PluginExclusion
import com.deezer.caupain.model.PluginInclusion
import com.deezer.caupain.model.Repository
import com.deezer.caupain.model.buildComponentFilter
import com.deezer.caupain.model.withComponentFilter
import kotlinx.serialization.decodeFromString
import okio.Path.Companion.toPath
import org.intellij.lang.annotations.Language
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfigurationParsingTest {

    @Test
    fun testParsing() {
        val result = DefaultToml.decodeFromString<Configuration>(CONFIGURATION)
        assertEquals(
            expected = listOf("versions.toml".toPath(), "other-versions.toml".toPath()),
            actual = result.versionCatalogPaths
        )
        assertEquals(
            expected = listOf(
                DefaultRepositories.mavenCentral.withComponentFilter {
                    include(group = "com.example", name = "example-lib")
                    include("com.example2.**")
                    exclude("com.other")
                },
                Repository(
                    url = "http://www.example.com/repo",
                    componentFilter = buildComponentFilter {
                        include(group = "com.example", name = "example-lib")
                        include("com.example2.**")
                        exclude("com.other")
                    },
                ),
                DefaultRepositories.google
            ),
            actual = result.repositories?.map { it.toModel() }
        )
        assertEquals(
            expected = listOf(
                DefaultRepositories.gradlePlugins,
                Repository(
                    url = "http://www.example.com/plugin"
                )
            ),
            actual = result.pluginRepositories?.map { it.toModel() }
        )
        assertEquals(setOf("stability-level", "other-one"), result.policies)
        assertEquals("build/cache/caupain".toPath(), result.cacheDir)
        assertEquals(setOf("testExcluded"), result.excludedKeys)
        assertEquals(setOf("testIncluded"), result.includedKeys)
        assertEquals(
            expected = listOf(
                LibraryExclusion("com.example.excluded", "example-lib"),
                LibraryExclusion("com.example2.excluded.**")
            ),
            actual = result.excludedLibraries
        )
        assertEquals(
            expected = listOf(
                LibraryInclusion("com.example.included", "example-lib"),
                LibraryInclusion("com.example2.included.**")
            ),
            actual = result.includedLibraries
        )
        assertEquals(
            expected = listOf(
                PluginExclusion("com.excluded.first"),
                PluginExclusion("com.excluded.second")
            ),
            actual = result.excludedPlugins
        )
        assertEquals(
            expected = listOf(
                PluginInclusion("com.included.first"),
                PluginInclusion("com.included.second")
            ),
            actual = result.includedPlugins
        )
        assertEquals(
            expected = setOf(Configuration.OutputType.MARKDOWN, Configuration.OutputType.HTML),
            actual = result.outputTypes
        )
        assertEquals(
            expected = listOf(
                Filter.LibraryFilter(
                    group = "com.example",
                    name = "example-lib",
                    versionFilter = GradleDependencyVersion.Prefix("1.+")
                ),
                Filter.LibraryFilter(
                    group = "com.example.**",
                    versionFilter = GradleDependencyVersion.Prefix("1.+")
                ),
                Filter.PluginFilter(
                    id = "com.example.plugin",
                    versionFilter = GradleDependencyVersion.Range("[1.0,)")
                )
            ),
            actual = result.filters,
        )
        assertEquals(true, result.doNotCheckSelfUpdates)
    }
}

@Language("TOML")
private val CONFIGURATION = """
versionCatalogPaths = ["versions.toml", "other-versions.toml"]
pluginRepositories = [ 
    "gradlePluginPortal",
    { url = "http://www.example.com/plugin" }
]
policies = ["stability-level", "other-one", "stability-level"]
filters = [
    { group = "com.example", name = "example-lib", versionFilter = "1.+" },
    { group = "com.example.**", versionFilter = "1.+" },
    { id = "com.example.plugin", versionFilter = "[1.0,)" }
]
cacheDir = "build/cache/caupain"
outputTypes = ["markdown", "html"]
outputPath = "build/reports/dependency-updates.md"    
excludedKeys = ["testExcluded"]
excludedLibraries = [
    { group = "com.example.excluded", name = "example-lib" },
    { group = "com.example2.excluded.**" }
]
excludedPlugins = [ "com.excluded.first", "com.excluded.second" ]
includedKeys = ["testIncluded"]
includedLibraries = [
    { group = "com.example.included", name = "example-lib" },
    { group = "com.example2.included.**" }
]
includedPlugins = [ "com.included.first", "com.included.second" ]
doNotCheckSelfUpdates = true
[[ repositories ]]
default = "mavenCentral"
includes = [
    { group = "com.example", name = "example-lib" },
    { group = "com.example2.**" }
]
excludes = [ 
    { group = "com.other" }
]
[[ repositories ]]
url = "http://www.example.com/repo"
includes = [
    { group = "com.example", name = "example-lib" },
    { group = "com.example2.**" }
]
excludes = [ 
    { group = "com.other" }
]
[[ repositories ]]
default = "google"
""".trimIndent()
