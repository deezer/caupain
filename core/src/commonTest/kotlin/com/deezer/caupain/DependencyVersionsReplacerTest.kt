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

import com.deezer.caupain.model.DependenciesUpdateResult
import com.deezer.caupain.model.Dependency
import com.deezer.caupain.model.GradleDependencyVersion
import com.deezer.caupain.model.Point
import com.deezer.caupain.model.UpdateInfo
import com.deezer.caupain.model.VersionCatalogInfo
import com.deezer.caupain.model.versionCatalog.Version
import com.deezer.caupain.model.versionCatalog.VersionCatalog
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
class DependencyVersionsReplacerTest {

    private lateinit var fileSystem: FakeFileSystem

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var replacer: DependencyVersionsReplacer

    @BeforeTest
    fun setup() {
        fileSystem = FakeFileSystem()
        replacer = DefaultDependencyVersionsReplacer(
            fileSystem = fileSystem,
            ioDispatcher = testDispatcher,
            defaultDispatcher = testDispatcher
        )
    }

    @AfterTest
    fun teardown() {
        fileSystem.checkNoOpenFiles()
        fileSystem.close()
    }

    @Test
    fun testReplacer() = runTest(testDispatcher) {
        val updateResult = DependenciesUpdateResult(
            gradleUpdateInfo = null,
            updateInfos = UPDATE_INFOS,
            ignoredUpdateInfos = emptyList(),
            selfUpdateInfo = null,
            versionCatalog = VERSION_CATALOG,
            versionCatalogInfo = VERSION_CATALOG_INFO
        )
        val versionCatalogPath = "libs.versions.toml".toPath()
        fileSystem.write(versionCatalogPath) {
            writeUtf8(ORIGIN_FILE)
        }
        replacer.replaceVersions(
            versionCatalogPath = versionCatalogPath,
            updateResult = updateResult
        )
        val updatedFileContent = fileSystem.read(versionCatalogPath) {
            readUtf8()
        }
        assertEquals(REPLACED_FILE, updatedFileContent)
    }
}

@Language("toml")
private val ORIGIN_FILE = """
[versions]
groovy = "3.0.5-alpha-1"
checkstyle = "8.37"

[libraries]
groovy-core = { module = "org.codehaus.groovy:groovy", version.ref = "groovy" }
groovy-json = { module = "org.codehaus.groovy:groovy-json", version.ref = "groovy" }
groovy-nio = { module = "org.codehaus.groovy:groovy-nio", version.ref = "groovy" }
commons-lang3 = { group = "org.apache.commons", name = "commons-lang3", version = { strictly = "[3.8, 4.0[", prefer="3.9" } }
commons-text = "org.apache.commons:commons-text:1.13.1"

[bundles]
groovy = ["groovy-core", "groovy-json", "groovy-nio"]

[plugins]
versions = { id = "com.github.ben-manes.versions", version = "0.45.0-SNAPSHOT" }
dokka = "org.jetbrains.dokka:2.0.0"
other = "com.example.other:1.0.0"
other2 = "com.example.other2:1.0.0"
""".trimIndent()

private val REPLACED_FILE = """
[versions]
groovy = "3.1.0"
checkstyle = "8.37"

[libraries]
groovy-core = { module = "org.codehaus.groovy:groovy", version.ref = "groovy" }
groovy-json = { module = "org.codehaus.groovy:groovy-json", version.ref = "groovy" }
groovy-nio = { module = "org.codehaus.groovy:groovy-nio", version.ref = "groovy" }
commons-lang3 = { group = "org.apache.commons", name = "commons-lang3", version = { strictly = "[3.8, 4.0[", prefer="3.9" } }
commons-text = "org.apache.commons:commons-text:1.20.0"

[bundles]
groovy = ["groovy-core", "groovy-json", "groovy-nio"]

[plugins]
versions = { id = "com.github.ben-manes.versions", version = "0.50.0" }
dokka = "org.jetbrains.dokka:3.0.0"
other = "com.example.other:1.0.0"
other2 = "com.example.other2:1.0.0"
""".trimIndent()

private val VERSION_CATALOG_INFO = VersionCatalogInfo(
    ignores = VersionCatalogInfo.Ignores(),
    positions = VersionCatalogInfo.Positions(
        versionRefsPositions = mapOf(
            "groovy" to VersionCatalogInfo.VersionPosition(
                startPoint = Point(1, 9),
                nbLines = 1,
                valueText = "\"3.0.5-alpha-1\""
            ),
            "checkstyle" to VersionCatalogInfo.VersionPosition(
                startPoint = Point(2, 13),
                nbLines = 1,
                valueText = "\"8.37\""
            )
        ),
        libraryVersionPositions = mapOf(
            "commons-text" to VersionCatalogInfo.VersionPosition(
                startPoint = Point(9, 15),
                nbLines = 1,
                valueText = "\"org.apache.commons:commons-text:1.13.1\""
            )
        ),
        pluginVersionPositions = mapOf(
            "versions" to VersionCatalogInfo.VersionPosition(
                startPoint = Point(15, 61),
                nbLines = 1,
                valueText = "\"0.45.0-SNAPSHOT\""
            ),
            "dokka" to VersionCatalogInfo.VersionPosition(
                startPoint = Point(16, 8),
                nbLines = 1,
                valueText = "\"org.jetbrains.dokka:2.0.0\""
            ),
            "other" to VersionCatalogInfo.VersionPosition(
                startPoint = Point(17, 8),
                nbLines = 1,
                valueText = "\"com.example.other:1.0.0\""
            ),
            "other2" to VersionCatalogInfo.VersionPosition(
                startPoint = Point(18, 9),
                nbLines = 1,
                valueText = "\"com.example.other2:1.0.0\""
            )
        )
    ),
)

private val VERSION_CATALOG = VersionCatalog(
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
        ),
        "commons-text" to Dependency.Library(
            module = "org.apache.commons:commons-text",
            version = Version.Simple(GradleDependencyVersion.Exact("1.13.1"))
        )
    ),
    plugins = mapOf(
        "versions" to Dependency.Plugin(
            id = "com.github.ben-manes.versions",
            version = Version.Simple(GradleDependencyVersion.Snapshot("0.45.0-SNAPSHOT"))
        ),
        "dokka" to Dependency.Plugin(
            id = "org.jetbrains.dokka",
            version = Version.Simple(GradleDependencyVersion.Exact("2.0.0"))
        ),
        "other" to Dependency.Plugin(
            id = "com.example.other",
            version = Version.Simple(GradleDependencyVersion.Exact("1.0.0"))
        ),
        "other2" to Dependency.Plugin(
            id = "com.example.other2",
            version = Version.Simple(GradleDependencyVersion.Exact("1.0.0"))
        )
    )
)

private val UPDATE_INFOS = mapOf(
    UpdateInfo.Type.LIBRARY to listOf(
        UpdateInfo(
            dependency = "groovy-core",
            dependencyId = "org.codehaus.groovy:groovy",
            currentVersion = "3.0.5-alpha-1".toSimpleVersion(),
            updatedVersion = "3.1.0".toStaticVersion()
        ),
        UpdateInfo(
            dependency = "groovy-json",
            dependencyId = "org.codehaus.groovy:groovy-json",
            currentVersion = "3.0.5-alpha-1".toSimpleVersion(),
            updatedVersion = "3.1.0".toStaticVersion()
        ),
        UpdateInfo(
            dependency = "commons-text",
            dependencyId = "org.apache.commons:commons-text",
            currentVersion = "1.13.1".toSimpleVersion(),
            updatedVersion = "1.20.0".toStaticVersion()
        )
    ),
    UpdateInfo.Type.PLUGIN to listOf(
        UpdateInfo(
            dependency = "versions",
            dependencyId = "org.codehaus.groovy:groovy",
            currentVersion = "0.45.0-SNAPSHOT".toSimpleVersion(),
            updatedVersion = "0.50.0".toStaticVersion()
        ),
        UpdateInfo(
            dependency = "dokka",
            dependencyId = "org.jetbrains.dokka",
            currentVersion = "2.0.0".toSimpleVersion(),
            updatedVersion = "3.0.0".toStaticVersion()
        ),
    )
)