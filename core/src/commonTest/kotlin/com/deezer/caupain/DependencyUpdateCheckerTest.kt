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
import com.deezer.caupain.model.GradleUpdateInfo
import com.deezer.caupain.model.Ignores
import com.deezer.caupain.model.LibraryExclusion
import com.deezer.caupain.model.Logger
import com.deezer.caupain.model.Repository
import com.deezer.caupain.model.SelfUpdateInfo
import com.deezer.caupain.model.UpdateInfo
import com.deezer.caupain.model.gradle.GradleConstants
import com.deezer.caupain.model.maven.MavenInfo
import com.deezer.caupain.model.maven.Metadata
import com.deezer.caupain.model.maven.Versioning
import com.deezer.caupain.model.versionCatalog.Version
import com.deezer.caupain.model.versionCatalog.VersionCatalog
import com.deezer.caupain.resolver.GradleVersionResolver
import com.deezer.caupain.resolver.GradleVersionResolverTest
import com.deezer.caupain.resolver.SelfUpdateResolver
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
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import com.deezer.caupain.model.maven.Dependency as MavenDependency
import com.deezer.caupain.model.maven.Version as MavenVersion

@OptIn(ExperimentalCoroutinesApi::class)
class DependencyUpdateCheckerTest {

    private lateinit var engine: MockEngine

    private lateinit var fileSystem: FakeFileSystem

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var checker: DependencyUpdateChecker

    @BeforeTest
    fun setup() {
        fileSystem = FakeFileSystem()
        val configuration = Configuration(
            repositories = listOf(SIGNED_REPOSITORY, BASE_REPOSITORY),
            pluginRepositories = listOf(BASE_REPOSITORY, SIGNED_REPOSITORY),
            excludedKeys = setOf("groovy-json"),
            excludedLibraries = listOf(LibraryExclusion(group = "org.apache.commons")),
            versionCatalogPaths = VERSION_CATALOGS.keys
        )
        for (versionCatalogPath in VERSION_CATALOGS.keys) {
            fileSystem.write(versionCatalogPath) {}
        }
        engine = MockEngine { requestData ->
            handleRequest(this, requestData)
                ?: respond("Not found", HttpStatusCode.NotFound)
        }
        checker = DefaultDependencyUpdateChecker(
            configuration = configuration,
            fileSystem = fileSystem,
            httpClient = HttpClient(engine) {
                install(ContentNegotiation) {
                    json(DefaultJson)
                    xml(DefaultXml, ContentType.Any)
                }
            },
            ioDispatcher = testDispatcher,
            versionCatalogParser = FixedVersionCatalogParser,
            logger = Logger.EMPTY,
            policies = emptyList(),
            currentGradleVersion = "8.11",
            selfUpdateResolver = FixedSelfUpdateResolver
        )
    }

    private fun handleRequest(
        scope: MockRequestHandleScope,
        requestData: HttpRequestData
    ): HttpResponseData? {
        val url = requestData.url
        // Check authentification
        if (url.host == SIGNED_URL.host) {
            val authHeader = requestData.headers[HttpHeaders.Authorization]
            if (authHeader != AUTHORIZATION) {
                return scope.respond("Unauthorized", HttpStatusCode.Unauthorized)
            }
        }
        return when (url) {
            GROOVY_CORE_METADATA_URL -> scope.respondElement(GROOVY_CORE_METADATA)

            GROOVY_CORE_INFO_URL -> scope.respondElement(GROOVY_CORE_INFO)

            GROOVY_NIO_METADATA_URL -> scope.respondElement(GROOVY_NIO_METADATA)

            GROOVY_NIO_INFO_URL -> scope.respondElement(GROOVY_NIO_INFO)

            VERSIONS_METADATA_URL -> scope.respondElement(VERSIONS_METADATA)

            VERSIONS_INFO_URL -> scope.respondElement(VERSIONS_INFO)

            RESOLVED_VERSIONS_INFO_URL -> scope.respondElement(RESOLVED_VERSIONS_INFO)

            GRADLE_VERSION_URL -> scope.respond(
                content = GradleVersionResolverTest.GRADLE_RELEASES,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )

            else -> null
        }
    }

    @AfterTest
    fun teardown() {
        engine.close()
        fileSystem.checkNoOpenFiles()
        fileSystem.close()
    }

    @Test
    fun testUpdate() = runTest(testDispatcher) {
        assertEquals(
            expected = DependenciesUpdateResult(
                gradleUpdateInfo = GradleUpdateInfo(
                    currentVersion = "8.11",
                    updatedVersion = "8.14.2",
                ),
                updateInfos = mapOf(
                    UpdateInfo.Type.LIBRARY to listOf(
                        UpdateInfo(
                            dependency = "groovy-core",
                            dependencyId = "org.codehaus.groovy:groovy",
                            name = "Groovy core",
                            url = "https://groovy-lang.org/",
                            currentVersion = "3.0.5-alpha-1".toSimpleVersion(),
                            updatedVersion = "3.0.6".toStaticVersion()
                        ),
                        UpdateInfo(
                            dependency = "groovy-nio",
                            dependencyId = "org.codehaus.groovy:groovy-nio",
                            name = "Groovy NIO",
                            url = "https://groovy-lang.org/",
                            currentVersion = "3.0.5-alpha-1".toSimpleVersion(),
                            updatedVersion = "3.0.5".toStaticVersion()
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
                versionCatalog = null,
                selfUpdateInfo = SELF_UPDATE_INFO
            ),
            actual = checker.checkForUpdates()
        )
        assertFalse(
            engine.requestHistory.any { it.url.toString().contains("groovy-other") },
            "Unexpected request for groovy-other"
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

        private const val USER = "user"
        private const val PASSWORD = "password"
        private const val AUTHORIZATION = "Basic dXNlcjpwYXNzd29yZA=="

        private val SIGNED_URL = Url("http://www.example.fr")

        private val SIGNED_REPOSITORY = Repository(
            url = SIGNED_URL.toString(),
            user = USER,
            password = PASSWORD
        )

        private object FixedVersionCatalogParser : VersionCatalogParser {
            override suspend fun parseDependencyInfo(versionCatalogPath: Path): VersionCatalogParseResult =
                VersionCatalogParseResult(
                    versionCatalog = VERSION_CATALOGS[versionCatalogPath]!!,
                    ignores = Ignores(libraryKeys = setOf("groovy-other"))
                )
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
            latest = "3.0.6",
            versions = listOf("3.0.5", "3.0.5-alpha-1", "3.0.6")
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
            .appendPathSegments("org", "codehaus", "groovy", "groovy", "3.0.6", "groovy-3.0.6.pom")
            .build()

        private val GROOVY_NIO_METADATA = metadata(
            latest = "3.0.5-alpha-1",
            versions = listOf("3.0.5", "3.0.5-alpha-1")
        )
        private val GROOVY_NIO_METADATA_URL = URLBuilder()
            .takeFrom(BASE_URL)
            .appendPathSegments("org", "codehaus", "groovy", "groovy-nio", "maven-metadata.xml")
            .build()

        private val GROOVY_NIO_INFO = info(
            name = "Groovy NIO",
            url = "https://groovy-lang.org/"
        )
        private val GROOVY_NIO_INFO_URL = URLBuilder()
            .takeFrom(BASE_URL)
            .appendPathSegments(
                "org",
                "codehaus",
                "groovy",
                "groovy-nio",
                "3.0.5",
                "groovy-nio-3.0.5.pom"
            )
            .build()

        private val VERSIONS_METADATA = metadata(
            latest = "1.0.0",
            versions = listOf("0.45.0-SNAPSHOT", "1.0.0")
        )
        private val VERSIONS_METADATA_URL = URLBuilder()
            .takeFrom(SIGNED_URL)
            .appendPathSegments(
                "com",
                "github",
                "ben-manes",
                "versions",
                "com.github.ben-manes.versions.gradle.plugin",
                "maven-metadata.xml"
            )
            .build()

        private val VERSIONS_INFO = info(
            name = null,
            url = null,
            dependency = "resolved" to "plugin"
        )
        private val VERSIONS_INFO_URL = URLBuilder()
            .takeFrom(SIGNED_URL)
            .appendPathSegments(
                "com",
                "github",
                "ben-manes",
                "versions",
                "com.github.ben-manes.versions.gradle.plugin",
                "1.0.0",
                "com.github.ben-manes.versions.gradle.plugin-1.0.0.pom"
            )
            .build()
        private val RESOLVED_VERSIONS_INFO = info(
            name = "Resolved plugin",
            url = "http://www.example.com/resolved",
        )
        private val RESOLVED_VERSIONS_INFO_URL = URLBuilder()
            .takeFrom(SIGNED_URL)
            .appendPathSegments(
                "resolved",
                "plugin",
                "1.0",
                "plugin-1.0.pom"
            )
            .build()

        private val GRADLE_VERSION_URL = Url(GradleConstants.DEFAULT_GRADLE_VERSIONS_URL)

        private val VERSION_CATALOG_MAIN = VersionCatalog(
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
                "groovy-other" to Dependency.Library(
                    module = "org.codehaus.groovy:groovy-other",
                    version = Version.Simple(GradleDependencyVersion.Exact("1.0"))
                ),
            ),
            plugins = mapOf(
                "versions" to Dependency.Plugin(
                    id = "com.github.ben-manes.versions",
                    version = Version.Simple(GradleDependencyVersion.Snapshot("0.45.0-SNAPSHOT"))
                )
            )
        )

        private val VERSION_CATALOG_OTHER = VersionCatalog(
            versions = mapOf(
                "groovy" to Version.Simple(GradleDependencyVersion.Exact("3.0.5-alpha-1")),
            ),
            libraries = mapOf(
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
        )

        private val VERSION_CATALOGS = mapOf(
            "libs.versions.toml".toPath() to VERSION_CATALOG_MAIN,
            "libs-other.versions.toml".toPath() to VERSION_CATALOG_OTHER
        )

        private val SELF_UPDATE_INFO = SelfUpdateInfo(
            currentVersion = "1.0.0",
            updatedVersion = "1.1.0",
            sources = SelfUpdateInfo.Source.entries
        )

        private object FixedSelfUpdateResolver : SelfUpdateResolver {
            override suspend fun resolveSelfUpdate(
                checker: DependencyUpdateChecker,
                versionCatalogs: List<VersionCatalog>
            ): SelfUpdateInfo? = SELF_UPDATE_INFO
        }
    }
}