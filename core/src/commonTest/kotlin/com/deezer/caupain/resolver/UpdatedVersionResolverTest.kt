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

package com.deezer.caupain.resolver

import com.deezer.caupain.model.AlwaysAcceptPolicy
import com.deezer.caupain.model.Dependency
import com.deezer.caupain.model.GradleDependencyVersion
import com.deezer.caupain.model.Logger
import com.deezer.caupain.model.Repository
import com.deezer.caupain.model.maven.MavenInfo
import com.deezer.caupain.model.maven.Metadata
import com.deezer.caupain.model.maven.SnapshotVersion
import com.deezer.caupain.model.maven.Versioning
import com.deezer.caupain.model.versionCatalog.Version
import com.deezer.caupain.serialization.xml.DefaultXml
import dev.mokkery.MockMode
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
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
import io.ktor.serialization.kotlinx.xml.xml
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.encodeToString
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import com.deezer.caupain.model.maven.Version as MavenVersion

@OptIn(ExperimentalCoroutinesApi::class)
class UpdatedVersionResolverTest {
    private lateinit var engine: MockEngine

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var logger: Logger

    private var hasError = false

    private var delay: Duration = Duration.ZERO

    private val didDelay = mutableMapOf<String, Boolean>()

    private var signedHits by atomic(0)
    private var baseHits by atomic(0)

    @BeforeTest
    fun setup() {
        logger = mock(MockMode.autoUnit)
        engine = MockEngine { requestData ->
            handleRequest(this, requestData)
                ?: respond("Not found", HttpStatusCode.NotFound)
        }
    }

    private suspend fun handleRequest(
        scope: MockRequestHandleScope,
        requestData: HttpRequestData
    ): HttpResponseData? {
        if (hasError) throw TestException()
        val url = requestData.url
        // Check authentification
        if (url.host == SIGNED_URL.host) {
            val authHeader = requestData.headers[HttpHeaders.Authorization]
            if (authHeader != AUTHORIZATION) {
                return scope.respond("Unauthorized", HttpStatusCode.Unauthorized)
            }
        }
        when (url.host) {
            SIGNED_URL.host -> signedHits++
            BASE_URL.host -> baseHits++
        }
        if (didDelay[url.toString()] != true) {
            didDelay[url.toString()] = true
            delay(delay)
        }
        return when (url) {
            GROOVY_CORE_METADATA_URL -> scope.respondElement(GROOVY_CORE_METADATA)
            GROOVY_NIO_METADATA_URL -> scope.respondElement(GROOVY_NIO_METADATA)
            VERSIONS_METADATA_URL -> scope.respondElement(VERSIONS_METADATA)
            GROOVY_CORE_POM_3_0_6_URL -> scope.respondElement(EMPTY_MAVEN_INFO)
            GROOVY_CORE_POM_3_0_5_URL -> scope.respondElement(EMPTY_MAVEN_INFO)
            GROOVY_NIO_POM_3_0_5_URL -> scope.respond("Not found", HttpStatusCode.NotFound)
            else -> null
        }
    }

    @AfterTest
    fun teardown() {
        engine.close()
        delay = Duration.ZERO
        baseHits = 0
        signedHits = 0
        hasError = false
    }

    @Test
    fun testUpdate() = runTest(testDispatcher) {
        val resolver = createResolver()
        assertEquals(
            expected = UpdatedVersionResolver.Result(
                currentVersion = Version.Simple(GradleDependencyVersion.Exact("3.0.5-alpha-1")),
                updatedVersion = GradleDependencyVersion.Exact("3.0.6"),
                repository = BASE_REPOSITORY
            ),
            actual = resolver.getUpdatedVersion(
                dependency = Dependency.Library(
                    module = "org.codehaus.groovy:groovy",
                    version = Version.Reference("groovy")
                ),
                versionReferences = VERSION_REFERENCES
            )
        )
        assertNull(
            resolver.getUpdatedVersion(
                dependency = Dependency.Library(
                    module = "org.codehaus.groovy:groovy-json",
                    version = Version.Reference("groovy")
                ),
                versionReferences = VERSION_REFERENCES
            )
        )
        assertEquals(
            expected = UpdatedVersionResolver.Result(
                currentVersion = Version.Simple(GradleDependencyVersion.Exact("3.0.5-alpha-1")),
                updatedVersion = GradleDependencyVersion.Exact("3.0.5"),
                repository = SIGNED_REPOSITORY
            ),
            actual = resolver.getUpdatedVersion(
                dependency = Dependency.Library(
                    module = "org.codehaus.groovy:groovy-nio",
                    version = Version.Reference("groovy")
                ),
                versionReferences = VERSION_REFERENCES
            )
        )
        assertEquals(
            expected = UpdatedVersionResolver.Result(
                currentVersion = Version.Simple(GradleDependencyVersion.Snapshot("0.45.0-SNAPSHOT")),
                updatedVersion = GradleDependencyVersion.Exact("1.0.0"),
                repository = SIGNED_REPOSITORY
            ),
            actual = resolver.getUpdatedVersion(
                dependency = Dependency.Plugin(
                    id = "com.github.ben-manes.versions",
                    version = Version.Simple(
                        GradleDependencyVersion.Snapshot("0.45.0-SNAPSHOT")
                    )
                ),
                versionReferences = VERSION_REFERENCES
            )
        )
    }

    @Test
    fun testError() = runTest(testDispatcher) {
        val resolver = createResolver()
        hasError = true
        assertNull(
            resolver.getUpdatedVersion(
                dependency = Dependency.Library(
                    module = "org.codehaus.groovy:groovy",
                    version = Version.Reference("groovy")
                ),
                versionReferences = VERSION_REFERENCES
            )
        )
        verify { logger.error(any(), any<TestException>()) }
    }

    @Test
    fun testTimeout() = runTest(testDispatcher) {
        val resolver = createResolver()
        delay = 2.seconds
        assertEquals(
            expected = UpdatedVersionResolver.Result(
                currentVersion = Version.Simple(GradleDependencyVersion.Exact("3.0.5-alpha-1")),
                updatedVersion = GradleDependencyVersion.Exact("3.0.5"),
                repository = SIGNED_REPOSITORY
            ),
            actual = resolver.getUpdatedVersion(
                dependency = Dependency.Library(
                    module = "org.codehaus.groovy:groovy-nio",
                    version = Version.Reference("groovy")
                ),
                versionReferences = VERSION_REFERENCES
            )
        )
        assertEquals(1, engine.requestHistory.size)
        assertEquals(0, baseHits)
        assertEquals(2, signedHits)
    }

    @Test
    fun testVerifyExistence_WhenPomExists() = runTest(testDispatcher) {
        val resolver = createResolver(verifyExistence = true)
        assertEquals(
            expected = UpdatedVersionResolver.Result(
                currentVersion = Version.Simple(GradleDependencyVersion.Exact("3.0.5-alpha-1")),
                updatedVersion = GradleDependencyVersion.Exact("3.0.6"),
                repository = BASE_REPOSITORY
            ),
            actual = resolver.getUpdatedVersion(
                dependency = Dependency.Library(
                    module = "org.codehaus.groovy:groovy",
                    version = Version.Reference("groovy")
                ),
                versionReferences = VERSION_REFERENCES
            )
        )
    }

    @Test
    fun testVerifyExistence_WhenLatestPomDoesNotExist() = runTest(testDispatcher) {
        val resolver = createResolver(verifyExistence = true)

        // groovy-nio has 3.0.5 as the latest, but this version doesn't have pom
        assertNull(
            resolver.getUpdatedVersion(
                dependency = Dependency.Library(
                    module = "org.codehaus.groovy:groovy-nio",
                    version = Version.Reference("groovy")
                ),
                versionReferences = VERSION_REFERENCES
            )
        )
    }

    private fun createResolver(verifyExistence: Boolean = false): DefaultUpdatedVersionResolver {
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) {
                xml(DefaultXml, ContentType.Any)
            }
            install(HttpRequestRetry) {
                retryOnException(maxRetries = 3, retryOnTimeout = true)
                exponentialDelay(baseDelayMs = 100)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 1_000
                connectTimeoutMillis = 500
                socketTimeoutMillis = 500
            }
        }
        return DefaultUpdatedVersionResolver(
            httpClient = httpClient,
            repositories = listOf(SIGNED_REPOSITORY, BASE_REPOSITORY),
            pluginRepositories = listOf(BASE_REPOSITORY, SIGNED_REPOSITORY),
            onlyCheckStaticVersions = true,
            policy = AlwaysAcceptPolicy,
            ioDispatcher = testDispatcher,
            logger = logger,
            verifyExistence = verifyExistence,
            mavenInfoResolver = MavenInfoResolver(
                httpClient = httpClient,
                ioDispatcher = testDispatcher,
                logger = logger
            )
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

        private fun metadata(
            latest: String,
            versions: List<String> = emptyList(),
            pomSnapshotVersion: String? = null
        ) = Metadata(
            versioning = Versioning(
                latest = GradleDependencyVersion(latest),
                versions = versions.map { MavenVersion(GradleDependencyVersion(it)) },
                snapshotVersions = listOfNotNull(
                    pomSnapshotVersion?.let { version ->
                        SnapshotVersion(
                            extension = "pom",
                            value = GradleDependencyVersion(version)
                        )
                    }
                )
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

        private val GROOVY_NIO_METADATA = metadata(
            latest = "3.0.5-alpha-1",
            versions = listOf("3.0.5", "3.0.5-alpha-1")
        )
        private val GROOVY_NIO_METADATA_URL = URLBuilder()
            .takeFrom(SIGNED_URL)
            .appendPathSegments("org", "codehaus", "groovy", "groovy-nio", "maven-metadata.xml")
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

        private val VERSION_REFERENCES = mapOf(
            "groovy" to Version.Simple(
                GradleDependencyVersion.Exact("3.0.5-alpha-1")
            ),
            "checkstyle" to Version.Simple(
                GradleDependencyVersion.Exact("8.37")
            )
        )

        private val EMPTY_MAVEN_INFO = MavenInfo()

        private val GROOVY_CORE_POM_3_0_6_URL = URLBuilder()
            .takeFrom(BASE_URL)
            .appendPathSegments("org", "codehaus", "groovy", "groovy", "3.0.6", "groovy-3.0.6.pom")
            .build()

        private val GROOVY_CORE_POM_3_0_5_URL = URLBuilder()
            .takeFrom(BASE_URL)
            .appendPathSegments("org", "codehaus", "groovy", "groovy", "3.0.5", "groovy-3.0.5.pom")
            .build()

        private val GROOVY_NIO_POM_3_0_5_URL = URLBuilder()
            .takeFrom(SIGNED_URL)
            .appendPathSegments("org", "codehaus", "groovy", "groovy-nio", "3.0.5", "groovy-nio-3.0.5.pom")
            .build()
    }

    private class TestException : IOException()
}
