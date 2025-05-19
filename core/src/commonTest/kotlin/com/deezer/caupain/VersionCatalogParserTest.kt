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

import com.deezer.caupain.model.Dependency
import com.deezer.caupain.model.GradleDependencyVersion
import com.deezer.caupain.model.Ignores
import com.deezer.caupain.model.versionCatalog.Version
import com.deezer.caupain.model.versionCatalog.VersionCatalog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.intellij.lang.annotations.Language
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class VersionCatalogParserTest {

    private lateinit var fileSystem: FileSystem

    private val filePath = "/fake/file/path".toPath()

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var parser: VersionCatalogParser

    @BeforeTest
    fun setup() {
        fileSystem = FakeFileSystem()
        fileSystem.createDirectories(filePath.parent!!)
        fileSystem.write(filePath) { writeUtf8(TEST_VERSION_CATALOG) }
        parser = DefaultVersionCatalogParser(
            fileSystem = fileSystem,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun testParsing() = runTest(testDispatcher) {
        assertEquals(
            expected = VersionCatalogParseResult(
                versionCatalog = VersionCatalog(
                    versions = mapOf(
                        "groovy" to Version.Simple(GradleDependencyVersion.Exact("3.0.5-alpha-1")),
                        "checkstyle" to Version.Simple(GradleDependencyVersion.Exact("8.37"))
                    ),
                    libraries = mapOf(
                        "groovy-core" to Dependency.Library(
                            module = "org.codehaus.groovy:groovy",
                            version = Version.Reference("groovy")
                        ),
                        "groovy-json" to Dependency.Library(
                            module = "org.codehaus.groovy:groovy-json",
                            version = Version.Reference("groovy")
                        ),
                        "groovy-nio" to Dependency.Library(
                            module = "org.codehaus.groovy:groovy-nio",
                            version = Version.Reference("groovy")
                        ),
                        "commons-lang3" to Dependency.Library(
                            group = "org.apache.commons",
                            name = "commons-lang3",
                            version = Version.Rich(
                                strictly = GradleDependencyVersion.Range("[3.8, 4.0["),
                                prefer = GradleDependencyVersion.Exact("3.9")
                            )
                        )
                    ),
                    plugins = mapOf(
                        "versions" to Dependency.Plugin(
                            id = "com.github.ben-manes.versions",
                            version = Version.Simple(GradleDependencyVersion.Snapshot("0.45.0-SNAPSHOT"))
                        )
                    )
                ),
                ignores = Ignores()
            ),
            actual = parser.parseDependencyInfo(filePath)
        )
    }
}

@Language("TOML")
private const val TEST_VERSION_CATALOG = """
[versions]
groovy = "3.0.5-alpha-1"
checkstyle = "8.37"

[libraries]
groovy-core = { module = "org.codehaus.groovy:groovy", version.ref = "groovy" }
groovy-json = { module = "org.codehaus.groovy:groovy-json", version.ref = "groovy" }
groovy-nio = { module = "org.codehaus.groovy:groovy-nio", version.ref = "groovy" }
commons-lang3 = { group = "org.apache.commons", name = "commons-lang3", version = { strictly = "[3.8, 4.0[", prefer="3.9" } }

[bundles]
groovy = ["groovy-core", "groovy-json", "groovy-nio"]

[plugins]
versions = { id = "com.github.ben-manes.versions", version = "0.45.0-SNAPSHOT" }
"""