package com.deezer.dependencies.cli

import com.deezer.dependencies.DependencyUpdateChecker
import com.deezer.dependencies.model.Configuration
import com.deezer.dependencies.model.UpdateInfo
import com.github.ajalt.clikt.command.test
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
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
import com.deezer.dependencies.cli.model.Configuration as ParsedConfiguration

@OptIn(ExperimentalCoroutinesApi::class)
class DependencyUpdateCheckerCliTest {

    private val mockProgressFlow = MutableStateFlow<DependencyUpdateChecker.Progress?>(null)

    private lateinit var checker: DependencyUpdateChecker

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var fileSystem: FakeFileSystem

    private lateinit var parsedConfiguration: ParsedConfiguration

    private val configurationPath = "config.toml".toPath()

    private val versionCatalogPath = "libs.versions.toml".toPath()

    private val policyPluginDir = "policies".toPath()

    private val cacheDir = "cache".toPath()

    @BeforeTest
    fun setup() {
        fileSystem = FakeFileSystem()
        fileSystem.write(versionCatalogPath) { writeUtf8("") }
        fileSystem.write(configurationPath) { writeUtf8("") }
        fileSystem.createDirectories(policyPluginDir)
        fileSystem.createDirectories(cacheDir)
        checker = mock {
            every { progress } returns mockProgressFlow
        }
        parsedConfiguration = mock()
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
        createUpdateChecker = { config, _, _ ->
            checkConfiguration(config)
            checker
        },
    )

    @Test
    fun checkHelp() = runTest(testDispatcher) {
        val cli = createCli()
        val result = cli.test(listOf("-h"))
        assertEquals(EXPECTED_HELP, result.output.trim())
    }

    @Test
    fun testComplete() = runTest(testDispatcher) {
        val output = mapOf(
            UpdateInfo.Type.LIBRARY to listOf(
                UpdateInfo(
                    dependency = "groovy-core",
                    dependencyId = "org.codehaus.groovy:groovy",
                    name = "Groovy core",
                    url = "https://groovy-lang.org/",
                    currentVersion = "3.0.5-alpha-1",
                    updatedVersion = "3.0.6"
                )
            ),
            UpdateInfo.Type.PLUGIN to listOf(
                UpdateInfo(
                    dependency = "versions",
                    dependencyId = "com.github.ben-manes.versions",
                    name = "Resolved plugin",
                    url = "http://www.example.com/resolved",
                    currentVersion = "0.45.0-SNAPSHOT",
                    updatedVersion = "1.0.0"
                )
            )
        )
        everySuspend { checker.checkForUpdates() } returns output
        val baseConfiguration = Configuration(
            versionCatalogPath = versionCatalogPath,
            excludedKeys = setOf("excluded"),
            policy = "custom",
            policyPluginDir = policyPluginDir,
            cacheDir = cacheDir
        )
        val mergedConfiguration = mock<Configuration>()
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
                policyPluginDir.toString(),
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
}

private val EXPECTED_HELP = """
Usage: dependency-update-checker [<options>]

Options:
  -i, --version-catalog=<path>      Version catalog path
  -e, --excluded=<text>             Excluded keys
  -c, --config=<path>               Configuration file
  --policy-plugin-dir=<path>        Custom policies plugin dir
  -p, --policy=<text>               Update policy
  -t, --output-type=(console|html)  Output type
  -o, --output=<path>               HTML output path
  --cache-dir=<path>                Cache directory
  -q, --quiet                       Suppress all output
  -v, --verbose                     Verbose output
  -h, --help                        Show this message and exit    
""".trimIndent().trim()

@Language("HTML")
private val EXPECTED_RESULT = """
<html>
  <head>
    <style>
        th,
        td {
          border: 1px solid rgb(160 160 160);
          padding: 8px 10px;
        }
        
        td {
          text-align: center;
        }
        
        tr:nth-of-type(even) {
          background-color: #eee;
        }
        
        table {
          border-collapse: collapse;
          border: 2px solid rgb(140 140 140);
          width: 100%;
        }  
        </style>
  </head>
  <body>
    <h1>Dependency updates</h1>
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
        <tr>
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
        <tr>
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