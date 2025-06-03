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

import com.deezer.caupain.formatting.model.computeVersionReferenceInfos
import com.deezer.caupain.model.Dependency.Library
import com.deezer.caupain.model.Dependency.Plugin
import com.deezer.caupain.model.GradleDependencyVersion
import com.deezer.caupain.model.UpdateInfo
import com.deezer.caupain.model.versionCatalog.*
import com.deezer.caupain.toSimpleVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComputeVersionReferenceInfosTest {

    @Test
    fun `test empty version catalog returns empty list`() {
        val catalog = VersionCatalog(
            versions = emptyMap(),
            libraries = emptyMap(),
            plugins = emptyMap()
        )
        val updateInfos = emptyMap<UpdateInfo.Type, List<UpdateInfo>>()

        val result = computeVersionReferenceInfos(catalog, updateInfos)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `test single library with version reference`() {
        val versionRef = "kotlin.version"
        val libraryKey = "kotlin"
        val currentVersion = "1.8.0".toSimpleVersion()
        val updatedVersion = GradleDependencyVersion("1.9.0") as GradleDependencyVersion.Static

        val catalog = VersionCatalog(
            versions = mapOf(versionRef to currentVersion),
            libraries = mapOf(
                libraryKey to Library(
                    group = "org.jetbrains.kotlin",
                    name = "kotlin-stdlib",
                    version = Version.Reference(versionRef)
                )
            ),
            plugins = emptyMap()
        )

        val updateInfos = mapOf(
            UpdateInfo.Type.LIBRARY to listOf(
                UpdateInfo(
                    dependency = libraryKey,
                    dependencyId = "org.jetbrains.kotlin:kotlin-stdlib",
                    currentVersion = currentVersion,
                    updatedVersion = updatedVersion
                )
            )
        )

        val result = computeVersionReferenceInfos(catalog, updateInfos)

        assertEquals(1, result.size)
        with(result.first()) {
            assertEquals(versionRef, id)
            assertEquals(listOf(libraryKey), libraryKeys)
            assertEquals(mapOf(libraryKey to updatedVersion), updatedLibraries)
            assertEquals(1, nbFullyUpdatedLibraries)
            assertEquals(updatedLibraries.keys, fullyUpdatedLibraries)
            assertTrue(pluginKeys.isEmpty())
            assertTrue(updatedPlugins.isEmpty())
            assertEquals(0, nbFullyUpdatedPlugins)
            assertEquals(updatedPlugins.keys, fullyUpdatedPlugins)
            assertEquals(currentVersion, this.currentVersion)
            assertEquals(updatedVersion, this.updatedVersion)
        }
    }

    @Test
    fun `test multiple dependencies sharing same version reference`() {
        val versionRef = "kotlin.version"
        val currentVersion = "1.8.0".toSimpleVersion()
        val updatedVersion = GradleDependencyVersion("1.9.0") as GradleDependencyVersion.Static

        val catalog = VersionCatalog(
            versions = mapOf(versionRef to currentVersion),
            libraries = mapOf(
                "kotlin-stdlib" to Library(
                    group = "org.jetbrains.kotlin",
                    name = "kotlin-stdlib",
                    version = Version.Reference(versionRef)
                ),
                "kotlin-reflect" to Library(
                    group = "org.jetbrains.kotlin",
                    name = "kotlin-reflect",
                    version = Version.Reference(versionRef)
                )
            ),
            plugins = mapOf(
                "kotlin" to Plugin(
                    id = "org.jetbrains.kotlin.jvm",
                    version = Version.Reference(versionRef)
                )
            )
        )

        val updateInfos = mapOf(
            UpdateInfo.Type.LIBRARY to listOf(
                UpdateInfo(
                    dependency = "kotlin-stdlib",
                    dependencyId = "org.jetbrains.kotlin:kotlin-stdlib",
                    currentVersion = currentVersion,
                    updatedVersion = updatedVersion
                ),
                UpdateInfo(
                    dependency = "kotlin-reflect",
                    dependencyId = "org.jetbrains.kotlin:kotlin-reflect",
                    currentVersion = currentVersion,
                    updatedVersion = updatedVersion
                )
            ),
            UpdateInfo.Type.PLUGIN to listOf(
                UpdateInfo(
                    dependency = "kotlin",
                    dependencyId = "org.jetbrains.kotlin.jvm",
                    currentVersion = currentVersion,
                    updatedVersion = updatedVersion
                )
            )
        )

        val result = computeVersionReferenceInfos(catalog, updateInfos)

        assertEquals(1, result.size)
        with(result.first()) {
            assertEquals(versionRef, id)
            assertEquals(listOf("kotlin-reflect", "kotlin-stdlib"), libraryKeys.sorted())
            assertEquals(
                mapOf(
                    "kotlin-stdlib" to updatedVersion,
                    "kotlin-reflect" to updatedVersion
                ),
                updatedLibraries
            )
            assertEquals(2, nbFullyUpdatedLibraries)
            assertEquals(updatedLibraries.keys, fullyUpdatedLibraries)
            assertEquals(listOf("kotlin"), pluginKeys)
            assertEquals(mapOf("kotlin" to updatedVersion), updatedPlugins)
            assertEquals(1, nbFullyUpdatedPlugins)
            assertEquals(updatedPlugins.keys, fullyUpdatedPlugins)
            assertEquals(currentVersion, this.currentVersion)
            assertEquals(updatedVersion, this.updatedVersion)
            assertTrue(isFullyUpdated)
        }
    }

    @Test
    fun `test version reference with no updates`() {
        val versionRef = "test.version"
        val currentVersion = "1.0.0".toSimpleVersion()

        val catalog = VersionCatalog(
            versions = mapOf(versionRef to currentVersion),
            libraries = mapOf(
                "test" to Library(
                    group = "com.example",
                    name = "test",
                    version = Version.Reference(versionRef)
                )
            ),
            plugins = emptyMap()
        )

        val updateInfos = emptyMap<UpdateInfo.Type, List<UpdateInfo>>()

        val result = computeVersionReferenceInfos(catalog, updateInfos)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `test version reference with different update versions available`() {
        val versionRef = "test.version"
        val currentVersion = "1.0.0".toSimpleVersion()

        val lowerUpdate = GradleDependencyVersion("1.1.0") as GradleDependencyVersion.Static
        val higherUpdate = GradleDependencyVersion("2.0.0") as GradleDependencyVersion.Static

        val catalog = VersionCatalog(
            versions = mapOf(versionRef to currentVersion),
            libraries = mapOf(
                "lib1" to Library(
                    group = "com.example",
                    name = "lib1",
                    version = Version.Reference(versionRef)
                ),
                "lib2" to Library(
                    group = "com.example",
                    name = "lib2",
                    version = Version.Reference(versionRef)
                )
            ),
            plugins = mapOf(
                "plugin1" to Plugin(
                    id = "com.example.plugin1",
                    version = Version.Reference(versionRef)
                )
            )
        )

        val updateInfos = mapOf(
            UpdateInfo.Type.LIBRARY to listOf(
                UpdateInfo(
                    dependency = "lib1",
                    dependencyId = "com.example:lib1",
                    currentVersion = currentVersion,
                    updatedVersion = lowerUpdate
                ),
                UpdateInfo(
                    dependency = "lib2",
                    dependencyId = "com.example:lib2",
                    currentVersion = currentVersion,
                    updatedVersion = higherUpdate
                )
            ),
            UpdateInfo.Type.PLUGIN to listOf(
                UpdateInfo(
                    dependency = "plugin1",
                    dependencyId = "com.example.plugin1",
                    currentVersion = currentVersion,
                    updatedVersion = lowerUpdate
                )
            )
        )

        val result = computeVersionReferenceInfos(catalog, updateInfos)

        assertEquals(1, result.size)
        with(result.first()) {
            assertEquals(versionRef, id)
            assertEquals(listOf("lib1", "lib2"), libraryKeys.sorted())
            assertEquals(
                mapOf(
                    "lib1" to lowerUpdate,
                    "lib2" to higherUpdate
                ),
                updatedLibraries
            )
            assertEquals(1, nbFullyUpdatedLibraries)
            assertEquals(setOf("lib2"), fullyUpdatedLibraries)
            assertEquals(listOf("plugin1"), pluginKeys)
            assertEquals(mapOf("plugin1" to lowerUpdate), updatedPlugins)
            assertEquals(0, nbFullyUpdatedPlugins)
            assertEquals(emptySet(), fullyUpdatedPlugins)
            assertEquals(currentVersion, this.currentVersion)
            assertEquals(higherUpdate, this.updatedVersion)
            assertFalse(isFullyUpdated)
        }
    }
}