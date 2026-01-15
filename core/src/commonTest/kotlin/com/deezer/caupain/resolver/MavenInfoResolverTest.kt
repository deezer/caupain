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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.encodeToString
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class MavenInfoResolverTest {
    private lateinit var engine: MockEngine

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var resolver: MavenInfoResolver

    private lateinit var logger: Logger

    private var hasError = false

    @BeforeTest
    fun setup() {
        logger = mock(MockMode.autoUnit)
        engine = MockEngine { requestData ->
            handleRequest(this, requestData)
                ?: respond("Not found", HttpStatusCode.NotFound)
        }
        resolver = MavenInfoResolver(
            httpClient = HttpClient(engine) {
                install(ContentNegotiation) {
                    xml(DefaultXml, ContentType.Any)
                }
            },
            ioDispatcher = testDispatcher,
            logger = logger
        )
    }

    private fun handleRequest(
        scope: MockRequestHandleScope,
        requestData: HttpRequestData
    ): HttpResponseData? {
        if (hasError) throw TestException()
        val url = requestData.url
        return when (url) {
            CLASSIC_INFO_URL -> scope.respondElement(CLASSIC_INFO)
            VERSIONS_INFO_URL -> scope.respondElement(VERSIONS_INFO)
            RESOLVED_VERSIONS_INFO_URL -> scope.respondElement(RESOLVED_VERSIONS_INFO)
            SNAPSHOT_METADATA_URL -> scope.respondElement(SNAPSHOT_METADATA)
            SNAPSHOT_INFO_URL -> scope.respondElement(SNAPSHOT_INFO)
            else -> null
        }
    }

    @AfterTest
    fun teardown() {
        engine.close()
        hasError = false
    }

    @Test
    fun testClassic() = runTest(testDispatcher) {
        assertEquals(
            expected = MavenInfo(
                name = CLASSIC_INFO.name,
                url = CLASSIC_INFO.url
            ),
            actual = resolver.getMavenInfo(
                dependency = Dependency.Library(
                    module = "com.example:classic",
                    version = Version.Simple(GradleDependencyVersion.Exact("0.9"))
                ),
                repository = BASE_REPOSITORY,
                updatedVersion = GradleDependencyVersion.Exact("1.0")
            )
        )
    }

    @Test
    fun testError() = runTest(testDispatcher) {
        hasError = true
        assertNull(
            resolver.getMavenInfo(
                dependency = Dependency.Library(
                    module = "com.example:classic",
                    version = Version.Simple(GradleDependencyVersion.Exact("0.9"))
                ),
                repository = BASE_REPOSITORY,
                updatedVersion = GradleDependencyVersion.Exact("1.0")
            )
        )
        verify { logger.error(any(), any<TestException>()) }
    }

    @Test
    fun testPlugin() = runTest(testDispatcher) {
        assertEquals(
            expected = MavenInfo(
                name = RESOLVED_VERSIONS_INFO.name,
                url = RESOLVED_VERSIONS_INFO.url
            ),
            actual = resolver.getMavenInfo(
                dependency = Dependency.Plugin(
                    id = "com.github.ben-manes.versions",
                    version = Version.Simple(GradleDependencyVersion.Exact("0.9"))
                ),
                repository = BASE_REPOSITORY,
                updatedVersion = GradleDependencyVersion.Exact("1.0.0")
            )
        )
    }

    @Test
    fun testSnapshot() = runTest(testDispatcher) {
        assertEquals(
            expected = MavenInfo(
                name = SNAPSHOT_INFO.name,
                url = SNAPSHOT_INFO.url,
            ),
            actual = resolver.getMavenInfo(
                dependency = Dependency.Library(
                    module = "com.example:snapshot",
                    version = Version.Simple(GradleDependencyVersion.Exact("0.9"))
                ),
                repository = BASE_REPOSITORY,
                updatedVersion = GradleDependencyVersion.Snapshot("4.0.0-beta-2-SNAPSHOT")
            )
        )
    }

    companion object Companion {
        private inline fun <reified T> MockRequestHandleScope.respondElement(element: T): HttpResponseData {
            return respond(
                content = DefaultXml.encodeToString(element),
                headers = headersOf(HttpHeaders.ContentType, "application/xml")
            )
        }

        private val BASE_URL = Url("http://www.example.com")
        private val BASE_REPOSITORY = Repository(BASE_URL.toString())

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
                        com.deezer.caupain.model.maven.Dependency(
                            groupId = group,
                            artifactId = artifact,
                            version = "1.0"
                        )
                    }
            )
        )

        private val CLASSIC_INFO = info(
            name = "Classic",
            url = "http://www.example.com/classic"
        )
        private val CLASSIC_INFO_URL = URLBuilder()
            .takeFrom(BASE_URL)
            .appendPathSegments("com", "example", "classic", "1.0", "classic-1.0.pom")
            .build()

        private val VERSIONS_INFO = info(
            name = null,
            url = null,
            dependency = "resolved" to "plugin"
        )
        private val VERSIONS_INFO_URL = URLBuilder()
            .takeFrom(BASE_URL)
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
            .takeFrom(BASE_URL)
            .appendPathSegments(
                "resolved",
                "plugin",
                "1.0",
                "plugin-1.0.pom"
            )
            .build()

        private val SNAPSHOT_METADATA = Metadata(
            Versioning(
                snapshotVersions = listOf(
                    SnapshotVersion(
                        extension = "pom",
                        value = GradleDependencyVersion.Exact("4.0.0-beta-2-20240702.052209-2")
                    ),
                )
            )
        )
        private val SNAPSHOT_METADATA_URL = URLBuilder()
            .takeFrom(BASE_URL)
            .appendPathSegments(
                "com",
                "example",
                "snapshot",
                "4.0.0-beta-2-SNAPSHOT",
                "maven-metadata.xml"
            )
            .build()
        private val SNAPSHOT_INFO = info(
            name = "Snapshot",
            url = "http://www.example.com/snapshot"
        )
        private val SNAPSHOT_INFO_URL = URLBuilder()
            .takeFrom(BASE_URL)
            .appendPathSegments(
                "com",
                "example",
                "snapshot",
                "4.0.0-beta-2-SNAPSHOT",
                "snapshot-4.0.0-beta-2-20240702.052209-2.pom"
            )
            .build()
    }

    private class TestException : IOException()
}
