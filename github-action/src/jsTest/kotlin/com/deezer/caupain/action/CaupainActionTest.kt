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

package com.deezer.caupain.action

import com.deezer.caupain.DependencyUpdateChecker
import com.deezer.caupain.NoVersionCatalogException
import com.deezer.caupain.model.Configuration
import com.deezer.caupain.model.DependenciesUpdateResult
import com.deezer.caupain.model.GradleConfiguration
import com.deezer.caupain.model.GradleDependencyVersion
import com.deezer.caupain.model.GradleUpdateInfo
import com.deezer.caupain.model.Policy
import com.deezer.caupain.model.UpdateInfo
import com.deezer.caupain.model.gradle.GradleStabilityLevel
import com.deezer.caupain.model.gradle.GradleVersion
import com.deezer.caupain.model.versionCatalog.Version
import com.deezer.caupain.resolver.UpdatedVersionResolver
import io.ktor.client.HttpClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CaupainActionTest {

    private lateinit var fileSystem: FakeFileSystem

    private val lines = mutableListOf<String>()

    private val testDispatcher = UnconfinedTestDispatcher()

    private var configuration: Configuration? = null

    private var gradleConfiguration: GradleConfiguration? = null

    private val outputPath = "output.txt".toPath()

    private val summaryPath = "summary.md".toPath()

    @BeforeTest
    fun setup() {
        fileSystem = FakeFileSystem()
    }

    @AfterTest
    fun teardown() {
        fileSystem.checkNoOpenFiles()
        fileSystem.close()
    }

    private suspend fun run(
        vararg inputs: Pair<String, String>,
        result: DependenciesUpdateResult = NO_UPDATES,
        failure: Throwable? = null,
        environmentVariables: Map<String, String> = emptyMap(),
    ): Int {
        val variables = buildMap {
            put("GITHUB_OUTPUT", outputPath.toString())
            put("GITHUB_STEP_SUMMARY", summaryPath.toString())
            put("RUNNER_TEMP", "runner-temp")
            putAll(environmentVariables)
            for ((name, value) in inputs) put("INPUT_$name", value)
        }
        return CaupainAction(
            environment = ActionEnvironment(variables),
            fileSystem = fileSystem,
            logger = ActionLogger { lines += it },
            print = { lines += it },
            ioDispatcher = testDispatcher,
            createUpdateChecker = { configuration, gradleConfiguration ->
                this.configuration = configuration
                this.gradleConfiguration = gradleConfiguration
                FakeUpdateChecker(result, failure)
            }
        ).run()
    }

    private fun outputs(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val outputLines = fileSystem.read(outputPath) { readUtf8() }.lines()
        var index = 0
        while (index < outputLines.size) {
            val line = outputLines[index]
            if (line.contains("<<")) {
                val delimiter = line.substringAfter("<<")
                val value = outputLines.drop(index + 1).takeWhile { it != delimiter }
                result[line.substringBefore("<<")] = value.joinToString("\n")
                index += value.size + 2
            } else {
                index++
            }
        }
        return result
    }

    private fun writeFile(path: String, content: String) {
        val file = path.toPath()
        file.parent?.let { fileSystem.createDirectories(it) }
        fileSystem.write(file) { writeUtf8(content) }
    }

    @Test
    fun testNoUpdates() = runTest {
        assertEquals(0, run())
        assertEquals(
            mapOf("has-updates" to "false", "updates-count" to "0", "report-paths" to ""),
            outputs()
        )
        assertTrue(lines.none { it.startsWith("::error") })
    }

    @Test
    fun testUpdates() = runTest {
        assertEquals(0, run(result = UPDATES))
        assertEquals("true", outputs()["has-updates"])
        assertEquals("1", outputs()["updates-count"])
        assertTrue(lines.any { it.contains("com.deezer:library") })
        assertTrue(fileSystem.read(summaryPath) { readUtf8() }.contains("com.deezer:library"))
    }

    @Test
    fun testGradleUpdateIsCounted() = runTest {
        val result = DependenciesUpdateResult(
            gradleUpdateInfo = GradleUpdateInfo(
                currentVersion = GradleVersion("1.0"),
                updatedVersion = GradleVersion("1.1"),
            ),
            updateInfos = UPDATES.updateInfos,
            ignoredUpdateInfos = emptyList(),
            selfUpdateInfo = null,
            versionCatalog = null,
            versionCatalogInfo = null,
        )
        run(result = result)
        assertEquals("2", outputs()["updates-count"])
    }

    @Test
    fun testFailOnUpdates() = runTest {
        assertEquals(1, run("FAIL_ON_UPDATES" to "true", result = UPDATES))
        assertTrue(lines.contains("::error::Dependency updates are available"))
        // Outputs are still set to let following steps use them
        assertEquals("true", outputs()["has-updates"])
    }

    @Test
    fun testFailOnUpdatesWithoutUpdates() = runTest {
        assertEquals(0, run("FAIL_ON_UPDATES" to "true"))
    }

    @Test
    fun testJobSummaryDisabled() = runTest {
        run("JOB_SUMMARY" to "false", result = UPDATES)
        assertFalse(fileSystem.exists(summaryPath))
    }

    @Test
    fun testJobSummaryWithoutSummaryFile() = runTest {
        // Running outside of GitHub Actions: no summary file, must not fail
        val action = CaupainAction(
            environment = ActionEnvironment(emptyMap()),
            fileSystem = fileSystem,
            logger = ActionLogger { lines += it },
            print = { lines += it },
            ioDispatcher = testDispatcher,
            createUpdateChecker = { _, _ -> FakeUpdateChecker(UPDATES, null) }
        )
        assertEquals(0, action.run())
    }

    @Test
    fun testDefaultConfiguration() = runTest {
        run()
        val configuration = assertNotNull(configuration)
        assertEquals(listOf(Configuration.DEFAULT_CATALOG_PATH), configuration.versionCatalogPaths.toList())
        assertEquals("runner-temp/caupain-cache".toPath(), configuration.cacheDir)
        assertFalse(configuration.searchReleaseNote)
        assertNull(configuration.githubToken)
        assertNull(gradleConfiguration)
    }

    @Test
    fun testInputsOverrideDefaults() = runTest {
        run(
            "VERSION_CATALOG" to "a.toml\nb.toml",
            "EXCLUDED" to "one",
            "POLICY" to "first,second",
            "CACHE_DIR" to "my-cache",
            "CLEAN_CACHE" to "true",
            "GRADLE_STABILITY_LEVEL" to "rc",
            "SEARCH_RELEASE_NOTES" to "true",
            "VERIFY_EXISTENCE" to "true",
        )
        val configuration = assertNotNull(configuration)
        assertEquals(listOf("a.toml".toPath(), "b.toml".toPath()), configuration.versionCatalogPaths.toList())
        assertEquals(setOf("one"), configuration.excludedKeys)
        assertEquals(listOf("first", "second"), configuration.policies.toList())
        assertEquals("my-cache".toPath(), configuration.cacheDir)
        assertTrue(configuration.cleanCache)
        assertEquals(GradleStabilityLevel.RC, configuration.gradleStabilityLevel)
        assertTrue(configuration.searchReleaseNote)
        assertTrue(configuration.verifyExistence)
    }

    @Test
    fun testNoCache() = runTest {
        run("NO_CACHE" to "true")
        assertNull(assertNotNull(configuration).cacheDir)
    }

    @Test
    fun testConfigurationFileIsUsed() = runTest {
        writeFile("caupain.toml", """versionCatalogPaths = ["from-config.toml"]""")
        assertEquals(0, run())
        assertEquals(
            listOf("from-config.toml".toPath()),
            assertNotNull(configuration).versionCatalogPaths.toList()
        )
    }

    @Test
    fun testInputsOverrideConfigurationFile() = runTest {
        writeFile("caupain.toml", """versionCatalogPaths = ["from-config.toml"]""")
        run("VERSION_CATALOG" to "from-input.toml")
        assertEquals(
            listOf("from-input.toml".toPath()),
            assertNotNull(configuration).versionCatalogPaths.toList()
        )
    }

    @Test
    fun testExplicitConfigurationFile() = runTest {
        writeFile("caupain.toml", """versionCatalogPaths = ["default.toml"]""")
        writeFile("config/custom.toml", """versionCatalogPaths = ["custom.toml"]""")
        assertEquals(0, run("CONFIG_FILE" to "config/custom.toml"))
        assertEquals(
            listOf("custom.toml".toPath()),
            assertNotNull(configuration).versionCatalogPaths.toList()
        )
    }

    @Test
    fun testMissingExplicitConfigurationFile() = runTest {
        assertEquals(1, run("CONFIG_FILE" to "missing.toml"))
        assertTrue(lines.any { it.startsWith("::error::") && it.contains("missing.toml") })
        assertNull(configuration)
    }

    @Test
    fun testPolicyPluginsAreIgnoredWithWarning() = runTest {
        writeFile("caupain.toml", """policyPluginDir = "plugins"""")
        assertEquals(0, run())
        assertTrue(lines.any { it.startsWith("::warning::Policy plugins are not supported") })
    }

    @Test
    fun testGithubTokenIsMasked() = runTest {
        run("GITHUB_TOKEN" to "secret-token")
        assertEquals("secret-token", assertNotNull(configuration).githubToken)
        assertTrue(lines.contains("::add-mask::secret-token"))
    }

    @Test
    fun testDefaultGithubTokenFromEnvironment() = runTest {
        val environmentVariables = mapOf("CAUPAIN_GITHUB_TOKEN" to "workflow-token")
        run(environmentVariables = environmentVariables)
        assertEquals("workflow-token", assertNotNull(configuration).githubToken)
        assertTrue(lines.contains("::add-mask::workflow-token"))
    }

    @Test
    fun testGithubTokenPrecedence() = runTest {
        val environmentVariables = mapOf("CAUPAIN_GITHUB_TOKEN" to "workflow-token")

        // The configuration file takes precedence over the environment...
        writeFile("caupain.toml", """githubToken = "file-token"""")
        run(environmentVariables = environmentVariables)
        assertEquals("file-token", assertNotNull(configuration).githubToken)

        // ... and the input takes precedence over the configuration file
        run("GITHUB_TOKEN" to "input-token", environmentVariables = environmentVariables)
        assertEquals("input-token", assertNotNull(configuration).githubToken)
    }

    @Test
    fun testGithubTokenFromConfigurationFileIsMasked() = runTest {
        writeFile("caupain.toml", """githubToken = "file-token"""")
        run()
        assertTrue(lines.contains("::add-mask::file-token"))
    }

    @Test
    fun testGradleConfigurationIsLoaded() = runTest {
        writeFile(
            "gradle/wrapper/gradle-wrapper.properties",
            "distributionUrl=https\\://services.gradle.org/distributions/gradle-8.14.2-bin.zip"
        )
        run()
        assertEquals("8.14.2", assertNotNull(gradleConfiguration).version)
    }

    @Test
    fun testFileReports() = runTest {
        assertEquals(
            0,
            run(
                "OUTPUT_TYPES" to "markdown\njson",
                "OUTPUT_DIR" to "reports",
                "OUTPUT_BASE_NAME" to "updates",
                "JOB_SUMMARY" to "false",
                result = UPDATES,
            )
        )
        assertTrue(fileSystem.exists("reports/updates.md".toPath()))
        assertTrue(fileSystem.exists("reports/updates.json".toPath()))
        assertEquals("reports/updates.md\nreports/updates.json", outputs()["report-paths"])
        assertTrue(lines.contains("Report generated at reports/updates.md"))
        // No console output when only file reports are requested
        assertTrue(lines.none { it.contains("com.deezer:library") })
    }

    @Test
    fun testDefaultReportLocation() = runTest {
        run("OUTPUT_TYPES" to "html")
        assertTrue(fileSystem.exists("build/reports/dependencies-update.html".toPath()))
    }

    @Test
    fun testOutputTypesFromConfigurationFile() = runTest {
        writeFile("caupain.toml", """outputTypes = ["json"]""")
        run()
        assertTrue(fileSystem.exists("build/reports/dependencies-update.json".toPath()))
    }

    @Test
    fun testOutputTypesInputOverridesConfigurationFile() = runTest {
        writeFile("caupain.toml", """outputTypes = ["json"]""")
        run("OUTPUT_TYPES" to "markdown")
        assertTrue(fileSystem.exists("build/reports/dependencies-update.md".toPath()))
        assertFalse(fileSystem.exists("build/reports/dependencies-update.json".toPath()))
    }

    @Test
    fun testInvalidInput() = runTest {
        assertEquals(1, run("OUTPUT_TYPES" to "pdf"))
        assertTrue(lines.any { it.startsWith("::error::") && it.contains("pdf") })
        assertNull(configuration)
    }

    @Test
    fun testCaupainExceptionFailsTheStep() = runTest {
        val failure = NoVersionCatalogException(listOf("libs.versions.toml".toPath()))
        assertEquals(1, run(failure = failure))
        assertTrue(lines.any { it.startsWith("::error::No version catalog found") })
    }

    @Test
    fun testUnexpectedExceptionFailsTheStep() = runTest {
        assertEquals(1, run(failure = IllegalStateException("boom")))
        assertTrue(lines.any { it.startsWith("::error::Unexpected error: boom") })
    }

    private class FakeUpdateChecker(
        private val result: DependenciesUpdateResult,
        private val failure: Throwable?,
    ) : DependencyUpdateChecker {
        override val progress: Flow<DependencyUpdateChecker.Progress?> = emptyFlow()
        override val policies: Sequence<Policy> = emptySequence()
        override val versionResolver: UpdatedVersionResolver
            get() = error("Not used")
        override val httpClient: HttpClient
            get() = error("Not used")

        override suspend fun checkForUpdates(): DependenciesUpdateResult {
            failure?.let { throw it }
            return result
        }
    }

    private companion object {
        val NO_UPDATES = DependenciesUpdateResult(
            gradleUpdateInfo = null,
            updateInfos = emptyMap(),
            ignoredUpdateInfos = emptyList(),
            selfUpdateInfo = null,
            versionCatalog = null,
            versionCatalogInfo = null,
        )

        val UPDATES = DependenciesUpdateResult(
            gradleUpdateInfo = null,
            updateInfos = mapOf(
                UpdateInfo.Type.LIBRARY to listOf(
                    UpdateInfo(
                        dependency = "library",
                        dependencyId = "com.deezer:library",
                        currentVersion = Version.Simple("1.0.0".toStaticVersion()),
                        updatedVersion = "2.0.0".toStaticVersion(),
                    )
                )
            ),
            ignoredUpdateInfos = emptyList(),
            selfUpdateInfo = null,
            versionCatalog = null,
            versionCatalogInfo = null,
        )

        fun String.toStaticVersion() = GradleDependencyVersion(this) as GradleDependencyVersion.Static
    }
}
