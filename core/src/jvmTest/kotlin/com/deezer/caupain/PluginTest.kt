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

import com.deezer.caupain.model.Configuration
import com.deezer.caupain.model.DependenciesUpdateResult
import com.deezer.caupain.model.Dependency
import com.deezer.caupain.model.GradleDependencyVersion
import com.deezer.caupain.model.Ignores
import com.deezer.caupain.model.Logger
import com.deezer.caupain.model.Repository
import com.deezer.caupain.model.StabilityLevelPolicy
import com.deezer.caupain.model.UpdateInfo
import com.deezer.caupain.model.maven.MavenInfo
import com.deezer.caupain.model.maven.Metadata
import com.deezer.caupain.model.maven.Versioning
import com.deezer.caupain.model.versionCatalog.Version
import com.deezer.caupain.model.versionCatalog.VersionCatalog
import com.deezer.caupain.serialization.DefaultJson
import com.deezer.caupain.serialization.DefaultXml
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import io.ktor.http.headersOf
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import io.ktor.serialization.kotlinx.xml.xml
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.use
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import com.deezer.caupain.model.maven.Dependency as MavenDependency
import com.deezer.caupain.model.maven.Version as MavenVersion

@OptIn(ExperimentalCoroutinesApi::class)
class PluginTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var engine: MockEngine

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var checker: DependencyUpdateChecker

    @Before
    fun setup() {
        val pluginDir = temporaryFolder.newFolder("plugins")
        copyPluginToPluginDir(pluginDir, "plugin.jar")
        val versionCatalogFile = temporaryFolder.newFile("libs.versions.toml")
        versionCatalogFile.writeBytes(ByteArray(0))
        val configuration = Configuration(
            repositories = listOf(BASE_REPOSITORY),
            pluginRepositories = listOf(BASE_REPOSITORY),
            policyPluginsDir = pluginDir.toOkioPath(),
            policy = "my-custom-policy",
            versionCatalogPath = versionCatalogFile.toOkioPath()
        )
        engine = MockEngine { requestData ->
            handleRequest(this, requestData)
                ?: respond("Not found", HttpStatusCode.NotFound)
        }
        checker = DefaultDependencyUpdateChecker(
            configuration = configuration,
            fileSystem = FileSystem.SYSTEM,
            httpClient = HttpClient(engine) {
                install(ContentNegotiation) {
                    json(DefaultJson)
                    xml(DefaultXml, ContentType.Any)
                }
            },
            ioDispatcher = testDispatcher,
            versionCatalogParser = FixedVersionCatalogParser,
            logger = Logger.EMPTY,
            currentGradleVersion = null,
            policies = null
        )
    }

    private fun copyPluginToPluginDir(pluginDir: File, pluginFileName: String) {
        javaClass
            .getResourceAsStream(pluginFileName)
            ?.buffered()
            ?.use { input ->
                File(pluginDir, pluginFileName)
                    .outputStream()
                    .buffered()
                    .use { output ->
                        input.copyTo(output)
                    }
            }
    }

    private fun handleRequest(
        scope: MockRequestHandleScope,
        requestData: HttpRequestData
    ): HttpResponseData? {
        val url = requestData.url
        return when (url) {
            GROOVY_CORE_METADATA_URL -> scope.respondElement(GROOVY_CORE_METADATA)
            GROOVY_CORE_INFO_URL -> scope.respondElement(GROOVY_CORE_INFO)
            GROOVY_NIO_METADATA_URL -> scope.respondElement(GROOVY_NIO_METADATA)
            else -> null
        }
    }

    @After
    fun teardown() {
        engine.close()
    }

    @Test
    fun testUpdate() = runTest(testDispatcher) {
        val policies = checker.policies.toList()
        assertEquals(2, policies.size)
        assertEquals(StabilityLevelPolicy.name, policies[0].name)
        assertEquals("my-custom-policy", policies[1].name)
        assertEquals(
            expected = DependenciesUpdateResult(
                gradleUpdateInfo = null,
                updateInfos = mapOf(
                    UpdateInfo.Type.LIBRARY to listOf(
                        UpdateInfo(
                            dependency = "will-update",
                            dependencyId = "org.codehaus.groovy:groovy",
                            name = "Groovy core",
                            url = "https://groovy-lang.org/",
                            currentVersion = "1.0.0".toSimpleVersion(),
                            updatedVersion = "1.0.1".toStaticVersion()
                        ),
                    ),
                    UpdateInfo.Type.PLUGIN to emptyList(),
                ),
                versionCatalog = VERSION_CATALOG
            ),
            actual = checker.checkForUpdates()
        )
        val hitUrls = engine.requestHistory.map { it.url }
        assertContentEquals(
            expected = EXPECTED_HIT_URLS.sortedBy { it.toString() },
            actual = hitUrls.sortedBy { it.toString() }
        )
    }

    companion object {
        private inline fun <reified T> MockRequestHandleScope.respondElement(element: T): HttpResponseData {
            return respond(
                content = DefaultXml.encodeToString(element),
                headers = headersOf(HttpHeaders.ContentType, "application/xml")
            )
        }

        private val BASE_URL = Url("http://www.example.com")
        private val BASE_REPOSITORY = Repository(BASE_URL.toString())

        private object FixedVersionCatalogParser : VersionCatalogParser {
            override suspend fun parseDependencyInfo(versionCatalogPath: Path) =
                VersionCatalogParseResult(VERSION_CATALOG, Ignores())
        }

        private fun metadata(
            latest: String,
            versions: List<String>
        ) = Metadata(
            versioning = Versioning(
                latest = GradleDependencyVersion(latest),
                versions = versions.map { MavenVersion(GradleDependencyVersion(it)) }
            )
        )

        private fun info(
            name: String?,
            url: String?,
            dependency: Pair<String, String>? = null
        ) = MavenInfo(
            name = name,
            url = url,
            dependencies = listOfNotNull(
                dependency
                    ?.let { (group, artifact) ->
                        MavenDependency(
                            groupId = group,
                            artifactId = artifact,
                            version = "1.0"
                        )
                    }
            )
        )

        private val GROOVY_CORE_METADATA = metadata(
            latest = "1.0.1",
            versions = listOf("1.0.0", "1.0.1")
        )
        private val GROOVY_CORE_METADATA_URL = URLBuilder()
            .takeFrom(BASE_URL)
            .appendPathSegments("org", "codehaus", "groovy", "groovy", "maven-metadata.xml")
            .build()

        private val GROOVY_CORE_INFO = info(
            name = "Groovy core",
            url = "https://groovy-lang.org/"
        )
        private val GROOVY_CORE_INFO_URL = URLBuilder()
            .takeFrom(BASE_URL)
            .appendPathSegments("org", "codehaus", "groovy", "groovy", "1.0.1", "groovy-1.0.1.pom")
            .build()

        private val GROOVY_NIO_METADATA = metadata(
            latest = "1.1.0",
            versions = listOf("1.0.0", "1.1.0")
        )
        private val GROOVY_NIO_METADATA_URL = URLBuilder()
            .takeFrom(BASE_URL)
            .appendPathSegments("org", "codehaus", "groovy", "groovy-nio", "maven-metadata.xml")
            .build()

        private val VERSION_CATALOG = VersionCatalog(
            libraries = mapOf(
                "will-update" to Dependency.Library(
                    module = "org.codehaus.groovy:groovy",
                    version = Version.Simple(GradleDependencyVersion.Exact("1.0.0"))
                ),
                "will-not-update" to Dependency.Library(
                    module = "org.codehaus.groovy:groovy-nio",
                    version = Version.Simple(GradleDependencyVersion.Exact("1.0.0"))
                ),
            )
        )

        private val EXPECTED_HIT_URLS =
            listOf(GROOVY_CORE_INFO_URL, GROOVY_CORE_METADATA_URL, GROOVY_NIO_METADATA_URL)
    }
}