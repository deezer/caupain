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

import com.deezer.caupain.DependencyUpdateChecker
import com.deezer.caupain.cli.internal.CAN_USE_PLUGINS
import com.deezer.caupain.model.Configuration
import com.deezer.caupain.model.DependenciesUpdateResult
import com.deezer.caupain.model.Dependency
import com.deezer.caupain.model.GradleUpdateInfo
import com.deezer.caupain.model.Policy
import com.deezer.caupain.model.SelfUpdateInfo
import com.deezer.caupain.model.UpdateInfo
import com.deezer.caupain.model.versionCatalog.Version
import com.deezer.caupain.model.versionCatalog.VersionCatalog
import com.github.ajalt.clikt.command.test
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.intellij.lang.annotations.Language
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import com.deezer.caupain.cli.model.Configuration as ParsedConfiguration

@OptIn(ExperimentalCoroutinesApi::class)
class DependencyUpdateCheckerCliTest {

    private val mockProgressFlow = MutableStateFlow<DependencyUpdateChecker.Progress?>(null)

    private lateinit var checker: DependencyUpdateChecker

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var fileSystem: FakeFileSystem

    private lateinit var parsedConfiguration: ParsedConfiguration

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
        val propertiesPath = "gradle-wrapper.properties".toPath()
        fileSystem.write(propertiesPath) { writeUtf8(GRADLE_WRAPPER_PROPERTIES) }
        parsedConfiguration = mock {
            every { gradleWrapperPropertiesPath } returns propertiesPath
            every { outputType } returns null
            every { outputPath } returns null
            every { validate(any()) } returns Unit
            every { showVersionReferences } returns true
        }
    }

    @AfterTest
    fun teardown() {
        fileSystem.checkNoOpenFiles()
        fileSystem.close()
    }

    private inline fun createCli(
        crossinline checkConfiguration: (Configuration) -> Unit = {},
    ) = DependencyUpdateCheckerCli(
        fileSystem = fileSystem,
        defaultDispatcher = testDispatcher,
        ioDispatcher = testDispatcher,
        parseConfiguration = { fs, path ->
            assertEquals(fileSystem, fs)
            assertEquals(configurationPath, path)
            parsedConfiguration
        },
        createUpdateChecker = { config, gradleVersion, _, _, _ ->
            assertEquals("8.11", gradleVersion)
            checkConfiguration(config)
            checker
        },
    )

    @Test
    fun testComplete() = runTest(testDispatcher) {
        val output = DependenciesUpdateResult(
            updateInfos = mapOf(
                UpdateInfo.Type.LIBRARY to listOf(
                    UpdateInfo(
                        dependency = "groovy-core",
                        dependencyId = "org.codehaus.groovy:groovy",
                        name = "Groovy core",
                        url = "https://groovy-lang.org/",
                        currentVersion = "3.0.5-alpha-1".toSimpleVersion(),
                        updatedVersion = "3.0.6".toStaticVersion()
                    )
                ),
                UpdateInfo.Type.PLUGIN to listOf(
                    UpdateInfo(
                        dependency = "versions",
                        dependencyId = "com.github.ben-manes.versions",
                        name = "Resolved plugin",
                        url = "http://www.example.com/resolved",
                        currentVersion = "0.45.0-SNAPSHOT".toSimpleVersion(),
                        updatedVersion = "1.0.0".toStaticVersion()
                    )
                )
            ),
            gradleUpdateInfo = GradleUpdateInfo(
                currentVersion = "8.11",
                updatedVersion = "8.13"
            ),
            versionCatalog = VersionCatalog(
                versions = mapOf("groovy" to "3.0.5-alpha-1".toSimpleVersion()),
                libraries = mapOf(
                    "groovy-core" to Dependency.Library(
                        group = "org.codehaus.groovy",
                        name = "groovy",
                        version = Version.Reference("groovy"),
                    )
                ),
                plugins = mapOf(
                    "versions" to Dependency.Plugin(
                        id = "com.github.ben-manes.versions",
                        version = "0.45.0-SNAPSHOT".toSimpleVersion()
                    )
                )
            ),
            ignoredUpdateInfos = emptyList(),
            selfUpdateInfo = SelfUpdateInfo(
                currentVersion = "1.0.0",
                updatedVersion = "1.1.0",
                sources = SelfUpdateInfo.Source.entries
            )
        )
        everySuspend { checker.checkForUpdates() } returns output
        val baseConfiguration = Configuration(
            versionCatalogPath = versionCatalogPath,
            excludedKeys = setOf("excluded"),
            policy = "custom",
            policyPluginsDir = mockPolicyPluginDir,
            cacheDir = cacheDir
        )
        val mergedConfiguration = mock<Configuration> {
            every { policyPluginsDir } returns if (CAN_USE_PLUGINS) mockPolicyPluginDir else null
        }
        every { parsedConfiguration.toConfiguration(baseConfiguration) } returns mergedConfiguration
        val cli = createCli { conf ->
            assertEquals(mergedConfiguration, conf)
        }
        val outputPath = "outputs/output.html".toPath()
        val result = cli.test(
            listOf(
                "-i",
                versionCatalogPath.toString(),
                "-e",
                "excluded",
                "-c",
                configurationPath.toString(),
                "--policy-plugin-dir",
                mockPolicyPluginDir.toString(),
                "-p",
                "custom",
                "-t",
                "html",
                "-o",
                outputPath.toString(),
                "--cache-dir",
                cacheDir.toString(),
                "-q"
            )
        )
        assertEquals(0, result.statusCode)
        assertEquals("", result.output)
        assertEquals(EXPECTED_RESULT, fileSystem.read(outputPath) { readUtf8().trim() })
    }

    @Test
    fun testListPolicies() = runTest(testDispatcher) {
        val mergedConfiguration = mock<Configuration> {
            every { policyPluginsDir } returns if (CAN_USE_PLUGINS) mockPolicyPluginDir else null
        }
        every { parsedConfiguration.toConfiguration(any()) } returns mergedConfiguration
        val policies = listOf(
            mock<Policy> {
                every { name } returns "test1"
                every { description } returns null
            },
            mock<Policy> {
                every { name } returns "test2"
                every { description } returns "Test policy 2"
            }
        )
        every { checker.policies } returns policies.asSequence()
        val cli = createCli { conf ->
            assertEquals(mergedConfiguration, conf)
        }
        val result = cli.test(
            listOf(
                "--list-policies",
                "-c",
                configurationPath.toString(),
            )
        )
        assertEquals(0, result.statusCode)
        assertEquals("""
            Available policies:
            - <no-policy>: Built-in default to update to the latest version available
            - test1
            - test2: Test policy 2
        """.trimIndent(), result.output.trim())
    }
}

@Language("HTML")
private val EXPECTED_RESULT = """
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
    <p>Gradle current version is 8.11 whereas last version is 8.13. See <a href="https://docs.gradle.org/8.13/release-notes.html">release note</a>.</p>
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
          <td>groovy</td>
          <td>3.0.5-alpha-1</td>
          <td>3.0.6</td>
          <td>Libraries: <a href="#update_LIBRARY_groovy-core">groovy-core</a></td>
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
          <th>URL</th>
        </tr>
        <tr id="update_LIBRARY_groovy-core">
          <td>org.codehaus.groovy:groovy</td>
          <td>Groovy core</td>
          <td>3.0.5-alpha-1</td>
          <td>3.0.6</td>
          <td><a href="https://groovy-lang.org/">https://groovy-lang.org/</a></td>
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
        <tr id="update_PLUGIN_versions">
          <td>com.github.ben-manes.versions</td>
          <td>Resolved plugin</td>
          <td>0.45.0-SNAPSHOT</td>
          <td>1.0.0</td>
          <td><a href="http://www.example.com/resolved">http://www.example.com/resolved</a></td>
        </tr>
      </table>
    </p>
  </body>
</html>    
""".trimIndent().trim()

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