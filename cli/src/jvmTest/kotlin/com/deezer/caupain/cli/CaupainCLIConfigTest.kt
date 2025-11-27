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

import ca.gosyer.appdirs.AppDirs
import com.deezer.caupain.DependencyUpdateChecker
import com.deezer.caupain.DependencyVersionsReplacer
import com.deezer.caupain.cli.model.DefaultRepository
import com.deezer.caupain.cli.model.Repository
import com.deezer.caupain.model.Configuration
import com.deezer.caupain.model.DefaultRepositories
import com.deezer.caupain.model.DependenciesUpdateResult
import com.deezer.caupain.model.LibraryExclusion
import com.deezer.caupain.model.Logger
import com.deezer.caupain.model.PluginExclusion
import com.deezer.caupain.model.StabilityLevelPolicy
import com.deezer.caupain.model.gradle.GradleStabilityLevel
import com.github.ajalt.clikt.command.test
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.intellij.lang.annotations.Language
import org.junit.runner.RunWith
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.deezer.caupain.cli.model.Configuration as ParsedConfiguration

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(TestParameterInjector::class)
class CaupainCLIConfigTest {

    private val mockProgressFlow = MutableStateFlow<DependencyUpdateChecker.Progress?>(null)

    private lateinit var checker: DependencyUpdateChecker

    private lateinit var replacer: DependencyVersionsReplacer

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var fileSystem: FakeFileSystem

    private lateinit var parsedConfiguration: TestConfiguration

    private val wrapperPropertiesPath = "gradle-wrapper.properties".toPath()

    private val configurationPath = "config.toml".toPath()

    private val versionCatalogPath = "libs.versions.toml".toPath()

    private val mockPolicyPluginDir = "policies".toPath()

    private val cacheDir = "cache".toPath()

    @BeforeTest
    fun setup() {
        fileSystem = FakeFileSystem()
        fileSystem.write(versionCatalogPath) { writeUtf8("") }
        fileSystem.write(configurationPath) { writeUtf8("") }
        fileSystem.createDirectories(mockPolicyPluginDir)
        fileSystem.createDirectories(cacheDir)
        checker = mock {
            every { progress } returns mockProgressFlow
        }
        replacer = mock(MockMode.autoUnit)
        fileSystem.write(wrapperPropertiesPath) { writeUtf8(GRADLE_WRAPPER_PROPERTIES) }
    }

    @AfterTest
    fun teardown() {
        fileSystem.checkNoOpenFiles()
        fileSystem.close()
    }

    private inline fun createCli(
        expectedGradleVersion: String?,
        crossinline checkConfiguration: (Configuration) -> Unit = {},
    ) = CaupainCLI(
        fileSystem = fileSystem,
        defaultDispatcher = testDispatcher,
        ioDispatcher = testDispatcher,
        parseConfiguration = { fs, path ->
            assertEquals(fileSystem, fs)
            assertEquals(configurationPath, path)
            parsedConfiguration
        },
        createUpdateChecker = { config, gradleVersion, _, _, _, _ ->
            assertEquals(expectedGradleVersion, gradleVersion)
            checkConfiguration(config)
            checker
        },
        createVersionReplacer = { _, _, _ -> replacer },
        appDirs = TestAppDirs
    )

    @Test
    fun testConfiguration(
        @TestParameter hasOptionsOverride: Boolean,
        @TestParameter hasConf: Boolean,
    ) {
        runTest(testDispatcher) {
            val gradleVersionCli = "1.0-CLI"
            val gradleWrapperPathCli = "gradleWrapperCLI".toPath()
            fileSystem.write(gradleWrapperPathCli) {
                writeUtf8(GRADLE_WRAPPER_PROPERTIES.replace("8.11", gradleVersionCli))
            }
            val gradleVersionConf = "1.0-Conf"
            val gradleWrapperPathConf = "gradleWrapperConf".toPath()
            fileSystem.write(gradleWrapperPathConf) {
                writeUtf8(GRADLE_WRAPPER_PROPERTIES.replace("8.11", gradleVersionConf))
            }
            fileSystem.write("path1".toPath()) { writeUtf8("") }
            fileSystem.write("path2".toPath()) { writeUtf8("") }
            if (hasConf) {
                parsedConfiguration = TestConfiguration(
                    repositories = listOf(Repository.Default(DefaultRepository.GOOGLE)),
                    pluginRepositories = listOf(Repository.Default(DefaultRepository.GOOGLE)),
                    versionCatalogPaths = listOf("path1".toPath(), "path2".toPath()),
                    excludedKeys = setOf("testConf"),
                    excludedLibraries = listOf(LibraryExclusion("libGroup", "libName")),
                    excludedPlugins = listOf(PluginExclusion("pluginId")),
                    policy = "policyConf",
                    policyPluginDir = "policyConf".toPath(),
                    cacheDir = "cacheConf".toPath(),
                    showVersionReferences = true,
                    outputTypes = listOf(
                        ParsedConfiguration.OutputType.HTML,
                        ParsedConfiguration.OutputType.JSON
                    ),
                    outputDir = "outputDirConf".toPath(),
                    outputBaseName = "outputBaseNameConf",
                    gradleWrapperPropertiesPath = gradleWrapperPathConf,
                    onlyCheckStaticVersions = false,
                    gradleStabilityLevel = GradleStabilityLevel.NIGHTLY,
                    checkIgnored = true,
                    searchReleaseNote = false,
                    githubToken = "githubTokenConf",
                    verifyExistence = true,
                )
            }
            when {
                hasOptionsOverride -> {
                    fileSystem.write("path3".toPath()) { writeUtf8("") }
                    fileSystem.createDirectories("outputDirCli".toPath())
                    fileSystem.createDirectories("policyCli".toPath())
                }

                hasConf -> {
                    fileSystem.createDirectories("outputDirConf".toPath())
                    fileSystem.createDirectories("policyConf".toPath())
                }

                else -> {
                    fileSystem.createDirectories(DEFAULT_OUTPUT_DIR.toPath())
                }
            }
            everySuspend { checker.checkForUpdates() } returns DependenciesUpdateResult(
                gradleUpdateInfo = null,
                updateInfos = emptyMap(),
                ignoredUpdateInfos = emptyList(),
                selfUpdateInfo = null,
                versionCatalog = null,
                versionCatalogInfo = null,
            )
            createCli(
                expectedGradleVersion = when {
                    hasOptionsOverride -> gradleVersionCli
                    hasConf -> gradleVersionConf
                    else -> null
                }
            ) { conf ->
                assertEquals(
                    expected = if (hasConf) {
                        listOf(DefaultRepositories.google)
                    } else {
                        Configuration.DEFAULT_REPOSITORIES
                    },
                    actual = conf.repositories
                )
                assertEquals(
                    expected = if (hasConf) {
                        listOf(DefaultRepositories.google)
                    } else {
                        Configuration.DEFAULT_PLUGIN_REPOSITORIES
                    },
                    actual = conf.pluginRepositories
                )
                assertEquals(
                    expected = when {
                        hasOptionsOverride -> listOf("path3".toPath())
                        hasConf -> listOf("path1".toPath(), "path2".toPath())
                        else -> listOf(Configuration.DEFAULT_CATALOG_PATH)
                    },
                    actual = conf.versionCatalogPaths
                )
                assertEquals(
                    expected = when {
                        hasOptionsOverride -> setOf("test")
                        hasConf -> setOf("testConf")
                        else -> emptySet()
                    },
                    actual = conf.excludedKeys
                )
                assertEquals(
                    expected = if (hasConf) {
                        listOf(LibraryExclusion("libGroup", "libName"))
                    } else {
                        emptyList()
                    },
                    actual = conf.excludedLibraries
                )
                assertEquals(
                    expected = if (hasConf) {
                        listOf(PluginExclusion("pluginId"))
                    } else {
                        emptyList()
                    },
                    actual = conf.excludedPlugins
                )
                assertEquals(
                    expected = when {
                        hasOptionsOverride -> "policyCli"
                        hasConf -> "policyConf"
                        else -> StabilityLevelPolicy.name
                    },
                    actual = conf.policy
                )
                assertEquals(
                    expected = when {
                        hasOptionsOverride -> "policyCli".toPath()
                        hasConf -> "policyConf".toPath()
                        else -> null
                    },
                    actual = conf.policyPluginsDir,
                )
                assertEquals(
                    expected = when {
                        hasOptionsOverride -> "cacheCli".toPath()
                        hasConf -> "cacheConf".toPath()
                        else -> TestAppDirs.USER_CACHE_DIR.toPath()
                    },
                    actual = conf.cacheDir,
                )
                assertEquals(!hasConf, conf.onlyCheckStaticVersions)
                assertEquals(
                    expected = when {
                        hasOptionsOverride -> GradleStabilityLevel.RC
                        hasConf -> GradleStabilityLevel.NIGHTLY
                        else -> GradleStabilityLevel.STABLE
                    },
                    actual = conf.gradleStabilityLevel
                )
                assertEquals(hasConf, conf.checkIgnored)
                assertEquals(
                    expected = when {
                        hasOptionsOverride -> true
                        hasConf -> false
                        else -> false
                    },
                    actual = conf.searchReleaseNote
                )
                assertEquals(
                    expected = when {
                        hasOptionsOverride -> "githubTokenCli"
                        hasConf -> "githubTokenConf"
                        else -> null
                    },
                    actual = conf.githubToken
                )
                assertEquals(hasConf, conf.verifyExistence)
            }.test(
                when {
                    hasOptionsOverride -> listOf(
                        "-i",
                        "path3",
                        "-e",
                        "test",
                        "-c",
                        if (hasConf) configurationPath.toString() else "wrong-path",
                        "--policy-plugin-dir",
                        "policyCli",
                        "-p",
                        "policyCli",
                        "--gradle-stability-level",
                        "rc",
                        "-t",
                        "markdown",
                        "--output-dir",
                        "outputDirCli",
                        "--output-base-name",
                        "outputBaseNameCli",
                        "--gradle-wrapper-properties",
                        gradleWrapperPathCli.toString(),
                        "--github-token",
                        "githubTokenCli",
                        "--cache-dir",
                        "cacheCli",
                        "--debug-http-calls",
                    )

                    hasConf -> listOf(
                        "-c",
                        configurationPath.toString(),
                    )

                    else -> emptyList()
                }
            )
            if (hasOptionsOverride) {
                assertTrue(fileSystem.exists("outputDirCli/outputBaseNameCli.md".toPath()))
            } else if (hasConf) {
                assertTrue(fileSystem.exists("outputDirConf/outputBaseNameConf.html".toPath()))
                assertTrue(fileSystem.exists("outputDirConf/outputBaseNameConf.json".toPath()))
            }
        }
    }
}

@Language("properties")
private val GRADLE_WRAPPER_PROPERTIES = """
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.11-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
""".trimIndent()

private data class TestConfiguration(
    override val repositories: List<Repository>?,
    override val pluginRepositories: List<Repository>?,
    override val versionCatalogPath: Path? = null,
    override val versionCatalogPaths: Iterable<Path>?,
    override val excludedKeys: Set<String>?,
    override val excludedLibraries: List<LibraryExclusion>?,
    override val excludedPlugins: List<PluginExclusion>?,
    override val policy: String?,
    override val policyPluginDir: Path?,
    override val cacheDir: Path?,
    override val showVersionReferences: Boolean?,
    override val outputType: ParsedConfiguration.OutputType? = null,
    override val outputTypes: Iterable<ParsedConfiguration.OutputType>?,
    override val outputPath: Path? = null,
    override val outputDir: Path?,
    override val outputBaseName: String?,
    override val gradleWrapperPropertiesPath: Path?,
    override val onlyCheckStaticVersions: Boolean?,
    override val gradleStabilityLevel: GradleStabilityLevel?,
    override val checkIgnored: Boolean?,
    override val searchReleaseNote: Boolean?,
    override val githubToken: String?,
    override val verifyExistence: Boolean?
) : ParsedConfiguration {

    var didValidate = false
        private set

    override fun validate(logger: Logger) {
        didValidate = true
    }
}

private object TestAppDirs : AppDirs {

    const val USER_CACHE_DIR = "test_user_cache_dir"

    override fun getSharedDir(): String = ""

    override fun getSiteConfigDir(multiPath: Boolean): String = ""

    override fun getSiteDataDir(multiPath: Boolean): String = ""

    override fun getUserCacheDir(): String = USER_CACHE_DIR

    override fun getUserConfigDir(roaming: Boolean): String = ""

    override fun getUserDataDir(roaming: Boolean): String = ""

    override fun getUserLogDir(): String = ""
}