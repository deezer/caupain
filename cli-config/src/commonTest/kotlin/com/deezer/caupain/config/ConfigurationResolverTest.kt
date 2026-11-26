/*
 * MIT License
 *
 * Copyright (c) 2026 Deezer
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
 *
 */

package com.deezer.caupain.config

import com.deezer.caupain.config.model.Configuration
import com.deezer.caupain.config.serialization.DefaultToml
import com.deezer.caupain.model.gradle.GradleStabilityLevel
import kotlinx.serialization.decodeFromString
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.intellij.lang.annotations.Language
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.deezer.caupain.model.Configuration as CoreConfiguration

class ConfigurationResolverTest {

    private lateinit var fileSystem: FakeFileSystem

    @BeforeTest
    fun setup() {
        fileSystem = FakeFileSystem()
    }

    @AfterTest
    fun teardown() {
        fileSystem.checkNoOpenFiles()
        fileSystem.close()
    }

    private fun parse(@Language("TOML") toml: String): Configuration =
        DefaultToml.decodeFromString<Configuration>(toml)

    @Test
    fun testDefaults() {
        val result = ConfigurationResolver.resolveConfiguration(parsedConfiguration = null)
        assertEquals(CoreConfiguration.DEFAULT_REPOSITORIES, result.repositories)
        assertEquals(CoreConfiguration.DEFAULT_PLUGIN_REPOSITORIES, result.pluginRepositories)
        assertEquals(listOf(CoreConfiguration.DEFAULT_CATALOG_PATH), result.versionCatalogPaths.toList())
        assertEquals(emptySet(), result.excludedKeys)
        assertEquals(listOf("stability-level"), result.policies.toList())
        assertNull(result.policyPluginsDir)
        assertNull(result.cacheDir)
        assertFalse(result.cleanCache)
        assertFalse(result.debugHttpCalls)
        assertTrue(result.onlyCheckStaticVersions)
        assertEquals(GradleStabilityLevel.STABLE, result.gradleStabilityLevel)
        assertFalse(result.checkIgnored)
        assertFalse(result.searchReleaseNote)
        assertNull(result.githubToken)
        assertFalse(result.verifyExistence)
    }

    @Test
    fun testEmptyConfigurationFileUsesDefaults() {
        val result = ConfigurationResolver.resolveConfiguration(parse(""))
        assertEquals(listOf(CoreConfiguration.DEFAULT_CATALOG_PATH), result.versionCatalogPaths.toList())
        assertEquals(listOf("stability-level"), result.policies.toList())
        assertEquals(GradleStabilityLevel.STABLE, result.gradleStabilityLevel)
    }

    @Test
    fun testConfigurationFileOverridesDefaults() {
        val result = ConfigurationResolver.resolveConfiguration(
            parse(
                """
                versionCatalogPaths = ["a.toml", "b.toml"]
                excludedKeys = ["one"]
                policies = ["first", "second"]
                cacheDir = "file-cache"
                onlyCheckStaticVersions = false
                gradleStabilityLevel = "rc"
                checkIgnored = true
                searchReleaseNote = true
                githubToken = "file-token"
                verifyExistence = true
                """.trimIndent()
            ),
            defaultCacheDir = { "default-cache".toPath() },
            defaultGithubToken = { "default-token" },
        )
        assertEquals(listOf("a.toml".toPath(), "b.toml".toPath()), result.versionCatalogPaths.toList())
        assertEquals(setOf("one"), result.excludedKeys)
        assertEquals(listOf("first", "second"), result.policies.toList())
        assertEquals("file-cache".toPath(), result.cacheDir)
        assertFalse(result.onlyCheckStaticVersions)
        assertEquals(GradleStabilityLevel.RC, result.gradleStabilityLevel)
        assertTrue(result.checkIgnored)
        assertTrue(result.searchReleaseNote)
        assertEquals("file-token", result.githubToken)
        assertTrue(result.verifyExistence)
    }

    @Test
    fun testOverridesTakePrecedenceOverConfigurationFile() {
        val result = ConfigurationResolver.resolveConfiguration(
            parse(
                """
                versionCatalogPaths = ["a.toml"]
                excludedKeys = ["one"]
                policies = ["first"]
                policyPluginDir = "file-plugins"
                cacheDir = "file-cache"
                gradleStabilityLevel = "rc"
                searchReleaseNote = false
                githubToken = "file-token"
                """.trimIndent()
            ),
            overrides = ConfigurationOverrides(
                versionCatalogPaths = listOf("override.toml".toPath()),
                excludedKeys = setOf("two"),
                policies = listOf(" other ", "another"),
                policyPluginsDir = "override-plugins".toPath(),
                cacheDir = "override-cache".toPath(),
                cleanCache = true,
                debugHttpCalls = true,
                gradleStabilityLevel = GradleStabilityLevel.NIGHTLY,
                searchReleaseNote = true,
                githubToken = "override-token",
                verifyExistence = true,
            ),
            defaultCacheDir = { "default-cache".toPath() },
            defaultGithubToken = { "default-token" },
        )
        assertEquals(listOf("override.toml".toPath()), result.versionCatalogPaths.toList())
        assertEquals(setOf("two"), result.excludedKeys)
        assertEquals(listOf("other", "another"), result.policies.toList())
        assertEquals("override-plugins".toPath(), result.policyPluginsDir)
        assertEquals("override-cache".toPath(), result.cacheDir)
        assertTrue(result.cleanCache)
        assertTrue(result.debugHttpCalls)
        assertEquals(GradleStabilityLevel.NIGHTLY, result.gradleStabilityLevel)
        assertTrue(result.searchReleaseNote)
        assertEquals("override-token", result.githubToken)
        assertTrue(result.verifyExistence)
    }

    @Test
    fun testDeprecatedSingleValuesInConfigurationFile() {
        val result = ConfigurationResolver.resolveConfiguration(
            parse(
                """
                versionCatalogPath = "single.toml"
                policy = "single-policy"
                """.trimIndent()
            )
        )
        assertEquals(listOf("single.toml".toPath()), result.versionCatalogPaths.toList())
        assertEquals(listOf("single-policy"), result.policies.toList())
    }

    @Test
    fun testDefaultCacheDirAndToken() {
        val result = ConfigurationResolver.resolveConfiguration(
            parsedConfiguration = parse(""),
            defaultCacheDir = { "default-cache".toPath() },
            defaultGithubToken = { "default-token" },
        )
        assertEquals("default-cache".toPath(), result.cacheDir)
        assertEquals("default-token", result.githubToken)
    }

    @Test
    fun testDisableCacheTakesPrecedenceOverEverything() {
        val result = ConfigurationResolver.resolveConfiguration(
            parsedConfiguration = parse("""cacheDir = "file-cache""""),
            overrides = ConfigurationOverrides(
                cacheDir = "override-cache".toPath(),
                disableCache = true,
            ),
            defaultCacheDir = { "default-cache".toPath() },
        )
        assertNull(result.cacheDir)
    }

    @Test
    fun testDefaultsAreNotComputedWhenNotNeeded() {
        ConfigurationResolver.resolveConfiguration(
            parsedConfiguration = parse(
                """
                cacheDir = "file-cache"
                githubToken = "file-token"
                """.trimIndent()
            ),
            defaultCacheDir = { error("Should not be called") },
            defaultGithubToken = { error("Should not be called") },
        )
        ConfigurationResolver.resolveConfiguration(
            parsedConfiguration = null,
            overrides = ConfigurationOverrides(disableCache = true),
            defaultCacheDir = { error("Should not be called") },
        )
    }

    @Test
    fun testGradleWrapperPropertiesPath() {
        assertEquals(
            ConfigurationResolver.DEFAULT_GRADLE_PROPERTIES_PATH,
            ConfigurationResolver.resolveGradleWrapperPropertiesPath(null)
        )
        assertEquals(
            "file.properties".toPath(),
            ConfigurationResolver.resolveGradleWrapperPropertiesPath(
                parse("""gradleWrapperPropertiesPath = "file.properties"""")
            )
        )
        assertEquals(
            "override.properties".toPath(),
            ConfigurationResolver.resolveGradleWrapperPropertiesPath(
                parsedConfiguration = parse("""gradleWrapperPropertiesPath = "file.properties""""),
                override = "override.properties".toPath()
            )
        )
    }

    @Test
    fun testLoadGradleConfigurationWithMissingFile() {
        assertNull(
            ConfigurationResolver.loadGradleConfiguration(fileSystem, "missing.properties".toPath())
        )
    }

    @Test
    fun testLoadGradleConfiguration() {
        val path = writeProperties(
            "distributionUrl=https\\://services.gradle.org/distributions/gradle-8.14.2-bin.zip"
        )
        val result = ConfigurationResolver.loadGradleConfiguration(fileSystem, path)
        assertEquals("8.14.2", result?.version)
        assertFalse(result?.needsChecksum == true)
    }

    @Test
    fun testLoadGradleConfigurationWithChecksum() {
        val path = writeProperties(
            "distributionUrl=https\\://services.gradle.org/distributions/gradle-8.14.2-all.zip",
            "distributionSha256Sum=abcdef"
        )
        val result = ConfigurationResolver.loadGradleConfiguration(fileSystem, path)
        assertEquals("8.14.2", result?.version)
        assertTrue(result?.needsChecksum == true)
    }

    @Test
    fun testLoadGradleConfigurationWithoutDistributionUrl() {
        val path = writeProperties("networkTimeout=10000")
        assertNull(ConfigurationResolver.loadGradleConfiguration(fileSystem, path))
    }

    private fun writeProperties(vararg lines: String): Path {
        val path = "gradle-wrapper.properties".toPath()
        fileSystem.write(path) { writeUtf8(lines.joinToString("\n")) }
        return path
    }
}
