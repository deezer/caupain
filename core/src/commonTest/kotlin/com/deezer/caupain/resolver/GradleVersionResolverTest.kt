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

import app.cash.burst.Burst
import app.cash.burst.burstValues
import com.deezer.caupain.model.Logger
import com.deezer.caupain.model.gradle.GradleStabilityLevel
import com.deezer.caupain.serialization.DefaultJson
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
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import org.intellij.lang.annotations.Language
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
@Burst
class GradleVersionResolverTest(
    private val testInfo: TestInfo = burstValues(
        TestInfo(GradleStabilityLevel.STABLE, "8.14.2"),
        TestInfo(GradleStabilityLevel.MILESTONE, "9.0.0-milestone-9"),
        TestInfo(GradleStabilityLevel.RC, "8.14.2"),
        TestInfo(GradleStabilityLevel.RELEASE_NIGHTLY, "9.0.0-20250616012057+0000"),
        TestInfo(GradleStabilityLevel.NIGHTLY, "9.1.0-20250616002551+0000"),
    )
) {
    private lateinit var engine: MockEngine

    private var hasError = false

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var resolver: GradleVersionResolver

    private lateinit var logger: Logger

    @BeforeTest
    fun setup() {
        logger = mock(MockMode.autoUnit)
        engine = MockEngine { requestData ->
            handleRequest(this, requestData)
                ?: respond("Not found", HttpStatusCode.NotFound)
        }
        resolver = GradleVersionResolver(
            httpClient = HttpClient(engine) {
                install(ContentNegotiation) {
                    json(DefaultJson, ContentType.Application.Json)
                }
            },
            logger = logger,
            gradleVersionsUrl = GRADLE_VERSIONS_URL.toString(),
            stabilityLevel = testInfo.stabilityLevel,
            ioDispatcher = testDispatcher
        )
    }

    private fun handleRequest(
        scope: MockRequestHandleScope,
        requestData: HttpRequestData
    ): HttpResponseData? {
        if (hasError) throw TestException()
        val url = requestData.url
        return if (url == GRADLE_VERSIONS_URL) {
            scope.respond(
                content = GRADLE_RELEASES,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        } else {
            null
        }
    }

    @AfterTest
    fun teardown() {
        engine.close()
        hasError = false
    }

    @Test
    fun testUpdate() = runTest(testDispatcher) {
        assertEquals(testInfo.expectedVersion, resolver.getUpdatedVersion("8.12"))
    }

    @Test
    fun testUpdateError() = runTest(testDispatcher) {
        hasError = true
        assertNull(resolver.getUpdatedVersion("8.12"))
        verify {
            logger.error(
                message = "Failed to fetch Gradle versions from $GRADLE_VERSIONS_URL",
                throwable = any<TestException>()
            )
        }
    }

    data class TestInfo(
        val stabilityLevel: GradleStabilityLevel,
        val expectedVersion: String
    ) {
        override fun toString(): String = stabilityLevel.name
    }

    private class TestException : IOException()

    @Suppress("LargeClass")
    companion object {
        private val BASE_URL = Url("http://www.example.com")

        private val GRADLE_VERSIONS_URL = URLBuilder()
            .takeFrom(BASE_URL)
            .appendPathSegments("gradle", "versions.json")
            .build()

        @Language("JSON")
        val GRADLE_RELEASES = """
            [ 
                {
                  "version" : "9.0.0-20250616012057+0000",
                  "buildTime" : "20250616012057+0000",
                  "current" : false,
                  "snapshot" : true,
                  "nightly" : false,
                  "releaseNightly" : true,
                  "activeRc" : false,
                  "rcFor" : "",
                  "milestoneFor" : "",
                  "broken" : false,
                  "downloadUrl" : "https://services.gradle.org/distributions-snapshots/gradle-9.0.0-20250616012057+0000-bin.zip",
                  "checksumUrl" : "https://services.gradle.org/distributions-snapshots/gradle-9.0.0-20250616012057+0000-bin.zip.sha256",
                  "wrapperChecksumUrl" : "https://services.gradle.org/distributions-snapshots/gradle-9.0.0-20250616012057+0000-wrapper.jar.sha256"
                }, {
                  "version" : "9.1.0-20250616002551+0000",
                  "buildTime" : "20250616002551+0000",
                  "current" : false,
                  "snapshot" : true,
                  "nightly" : true,
                  "releaseNightly" : false,
                  "activeRc" : false,
                  "rcFor" : "",
                  "milestoneFor" : "",
                  "broken" : false,
                  "downloadUrl" : "https://services.gradle.org/distributions-snapshots/gradle-9.1.0-20250616002551+0000-bin.zip",
                  "checksumUrl" : "https://services.gradle.org/distributions-snapshots/gradle-9.1.0-20250616002551+0000-bin.zip.sha256",
                  "wrapperChecksumUrl" : "https://services.gradle.org/distributions-snapshots/gradle-9.1.0-20250616002551+0000-wrapper.jar.sha256"
                }, {
                  "version" : "8.14.2",
                  "buildTime" : "20250605133201+0000",
                  "current" : true,
                  "snapshot" : false,
                  "nightly" : false,
                  "releaseNightly" : false,
                  "activeRc" : false,
                  "rcFor" : "",
                  "milestoneFor" : "",
                  "broken" : false,
                  "downloadUrl" : "https://services.gradle.org/distributions/gradle-8.14.2-bin.zip",
                  "checksumUrl" : "https://services.gradle.org/distributions/gradle-8.14.2-bin.zip.sha256",
                  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-8.14.2-wrapper.jar.sha256"
                }, {
                  "version" : "7.6.5",
                  "buildTime" : "20250604130222+0000",
                  "current" : false,
                  "snapshot" : false,
                  "nightly" : false,
                  "releaseNightly" : false,
                  "activeRc" : false,
                  "rcFor" : "",
                  "milestoneFor" : "",
                  "broken" : false,
                  "downloadUrl" : "https://services.gradle.org/distributions/gradle-7.6.5-bin.zip",
                  "checksumUrl" : "https://services.gradle.org/distributions/gradle-7.6.5-bin.zip.sha256",
                  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-7.6.5-wrapper.jar.sha256"
                }, {
                  "version" : "9.0.0-milestone-9",
                  "buildTime" : "20250526083131+0000",
                  "current" : false,
                  "snapshot" : false,
                  "nightly" : false,
                  "releaseNightly" : false,
                  "activeRc" : false,
                  "rcFor" : "",
                  "milestoneFor" : "9.0.0",
                  "broken" : false,
                  "downloadUrl" : "https://services.gradle.org/distributions/gradle-9.0.0-milestone-9-bin.zip",
                  "checksumUrl" : "https://services.gradle.org/distributions/gradle-9.0.0-milestone-9-bin.zip.sha256",
                  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-9.0.0-milestone-9-wrapper.jar.sha256"
                }, {
                  "version" : "8.14.1",
                  "buildTime" : "20250522134409+0000",
                  "current" : false,
                  "snapshot" : false,
                  "nightly" : false,
                  "releaseNightly" : false,
                  "activeRc" : false,
                  "rcFor" : "",
                  "milestoneFor" : "",
                  "broken" : false,
                  "downloadUrl" : "https://services.gradle.org/distributions/gradle-8.14.1-bin.zip",
                  "checksumUrl" : "https://services.gradle.org/distributions/gradle-8.14.1-bin.zip.sha256",
                  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-8.14.1-wrapper.jar.sha256"
                }, {
                  "version" : "9.0.0-milestone-8",
                  "buildTime" : "20250516073511+0000",
                  "current" : false,
                  "snapshot" : false,
                  "nightly" : false,
                  "releaseNightly" : false,
                  "activeRc" : false,
                  "rcFor" : "",
                  "milestoneFor" : "9.0.0",
                  "broken" : false,
                  "downloadUrl" : "https://services.gradle.org/distributions/gradle-9.0.0-milestone-8-bin.zip",
                  "checksumUrl" : "https://services.gradle.org/distributions/gradle-9.0.0-milestone-8-bin.zip.sha256",
                  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-9.0.0-milestone-8-wrapper.jar.sha256"
                }, {
                  "version" : "9.0-milestone-7",
                  "buildTime" : "20250513065613+0000",
                  "current" : false,
                  "snapshot" : false,
                  "nightly" : false,
                  "releaseNightly" : false,
                  "activeRc" : false,
                  "rcFor" : "",
                  "milestoneFor" : "9.0",
                  "broken" : false,
                  "downloadUrl" : "https://services.gradle.org/distributions/gradle-9.0-milestone-7-bin.zip",
                  "checksumUrl" : "https://services.gradle.org/distributions/gradle-9.0-milestone-7-bin.zip.sha256",
                  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-9.0-milestone-7-wrapper.jar.sha256"
                }, {
                  "version" : "9.0-milestone-6",
                  "buildTime" : "20250508062448+0000",
                  "current" : false,
                  "snapshot" : false,
                  "nightly" : false,
                  "releaseNightly" : false,
                  "activeRc" : false,
                  "rcFor" : "",
                  "milestoneFor" : "9.0",
                  "broken" : false,
                  "downloadUrl" : "https://services.gradle.org/distributions/gradle-9.0-milestone-6-bin.zip",
                  "checksumUrl" : "https://services.gradle.org/distributions/gradle-9.0-milestone-6-bin.zip.sha256",
                  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-9.0-milestone-6-wrapper.jar.sha256"
                }, {
                  "version" : "9.0-milestone-5",
                  "buildTime" : "20250429093659+0000",
                  "current" : false,
                  "snapshot" : false,
                  "nightly" : false,
                  "releaseNightly" : false,
                  "activeRc" : false,
                  "rcFor" : "",
                  "milestoneFor" : "9.0",
                  "broken" : false,
                  "downloadUrl" : "https://services.gradle.org/distributions/gradle-9.0-milestone-5-bin.zip",
                  "checksumUrl" : "https://services.gradle.org/distributions/gradle-9.0-milestone-5-bin.zip.sha256",
                  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-9.0-milestone-5-wrapper.jar.sha256"
                }, {
                  "version" : "9.0-milestone-4",
                  "buildTime" : "20250428144937+0000",
                  "current" : false,
                  "snapshot" : false,
                  "nightly" : false,
                  "releaseNightly" : false,
                  "activeRc" : false,
                  "rcFor" : "",
                  "milestoneFor" : "9.0",
                  "broken" : false,
                  "downloadUrl" : "https://services.gradle.org/distributions/gradle-9.0-milestone-4-bin.zip",
                  "checksumUrl" : "https://services.gradle.org/distributions/gradle-9.0-milestone-4-bin.zip.sha256",
                  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-9.0-milestone-4-wrapper.jar.sha256"
                }, {
                  "version" : "8.14",
                  "buildTime" : "20250425092908+0000",
                  "current" : false,
                  "snapshot" : false,
                  "nightly" : false,
                  "releaseNightly" : false,
                  "activeRc" : false,
                  "rcFor" : "",
                  "milestoneFor" : "",
                  "broken" : false,
                  "downloadUrl" : "https://services.gradle.org/distributions/gradle-8.14-bin.zip",
                  "checksumUrl" : "https://services.gradle.org/distributions/gradle-8.14-bin.zip.sha256",
                  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-8.14-wrapper.jar.sha256"
                }, {
                  "version" : "8.14-rc-3",
                  "buildTime" : "20250423120032+0000",
                  "current" : false,
                  "snapshot" : false,
                  "nightly" : false,
                  "releaseNightly" : false,
                  "activeRc" : false,
                  "rcFor" : "8.14",
                  "milestoneFor" : "",
                  "broken" : false,
                  "downloadUrl" : "https://services.gradle.org/distributions/gradle-8.14-rc-3-bin.zip",
                  "checksumUrl" : "https://services.gradle.org/distributions/gradle-8.14-rc-3-bin.zip.sha256",
                  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-8.14-rc-3-wrapper.jar.sha256"
                }, {
                  "version" : "9.0-milestone-3",
                  "buildTime" : "20250422030543+0000",
                  "current" : false,
                  "snapshot" : false,
                  "nightly" : false,
                  "releaseNightly" : false,
                  "activeRc" : false,
                  "rcFor" : "",
                  "milestoneFor" : "9.0",
                  "broken" : false,
                  "downloadUrl" : "https://services.gradle.org/distributions/gradle-9.0-milestone-3-bin.zip",
                  "checksumUrl" : "https://services.gradle.org/distributions/gradle-9.0-milestone-3-bin.zip.sha256",
                  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-9.0-milestone-3-wrapper.jar.sha256"
                }, {
                  "version" : "8.14-rc-2",
                  "buildTime" : "20250417124738+0000",
                  "current" : false,
                  "snapshot" : false,
                  "nightly" : false,
                  "releaseNightly" : false,
                  "activeRc" : false,
                  "rcFor" : "8.14",
                  "milestoneFor" : "",
                  "broken" : false,
                  "downloadUrl" : "https://services.gradle.org/distributions/gradle-8.14-rc-2-bin.zip",
                  "checksumUrl" : "https://services.gradle.org/distributions/gradle-8.14-rc-2-bin.zip.sha256",
                  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-8.14-rc-2-wrapper.jar.sha256"
                }, {
                  "version" : "9.0-milestone-2",
                  "buildTime" : "20250414083159+0000",
                  "current" : false,
                  "snapshot" : false,
                  "nightly" : false,
                  "releaseNightly" : false,
                  "activeRc" : false,
                  "rcFor" : "",
                  "milestoneFor" : "9.0",
                  "broken" : false,
                  "downloadUrl" : "https://services.gradle.org/distributions/gradle-9.0-milestone-2-bin.zip",
                  "checksumUrl" : "https://services.gradle.org/distributions/gradle-9.0-milestone-2-bin.zip.sha256",
                  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-9.0-milestone-2-wrapper.jar.sha256"
                }, {
                  "version" : "9.0-milestone-1",
                  "buildTime" : "20250410115011+0000",
                  "current" : false,
                  "snapshot" : false,
                  "nightly" : false,
                  "releaseNightly" : false,
                  "activeRc" : false,
                  "rcFor" : "",
                  "milestoneFor" : "9.0",
                  "broken" : false,
                  "downloadUrl" : "https://services.gradle.org/distributions/gradle-9.0-milestone-1-bin.zip",
                  "checksumUrl" : "https://services.gradle.org/distributions/gradle-9.0-milestone-1-bin.zip.sha256",
                  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-9.0-milestone-1-wrapper.jar.sha256"
                }, {
                  "version" : "8.14-rc-1",
                  "buildTime" : "20250409084650+0000",
                  "current" : false,
                  "snapshot" : false,
                  "nightly" : false,
                  "releaseNightly" : false,
                  "activeRc" : false,
                  "rcFor" : "8.14",
                  "milestoneFor" : "",
                  "broken" : false,
                  "downloadUrl" : "https://services.gradle.org/distributions/gradle-8.14-rc-1-bin.zip",
                  "checksumUrl" : "https://services.gradle.org/distributions/gradle-8.14-rc-1-bin.zip.sha256",
                  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-8.14-rc-1-wrapper.jar.sha256"
                }, {
                  "version" : "8.14-milestone-8",
                  "buildTime" : "20250408011208+0000",
                  "current" : false,
                  "snapshot" : false,
                  "nightly" : false,
                  "releaseNightly" : false,
                  "activeRc" : false,
                  "rcFor" : "",
                  "milestoneFor" : "8.14",
                  "broken" : false,
                  "downloadUrl" : "https://services.gradle.org/distributions/gradle-8.14-milestone-8-bin.zip",
                  "checksumUrl" : "https://services.gradle.org/distributions/gradle-8.14-milestone-8-bin.zip.sha256",
                  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-8.14-milestone-8-wrapper.jar.sha256"
                }, {
                  "version" : "8.14-milestone-7",
                  "buildTime" : "20250324073617+0000",
                  "current" : false,
                  "snapshot" : false,
                  "nightly" : false,
                  "releaseNightly" : false,
                  "activeRc" : false,
                  "rcFor" : "",
                  "milestoneFor" : "8.14",
                  "broken" : false,
                  "downloadUrl" : "https://services.gradle.org/distributions/gradle-8.14-milestone-7-bin.zip",
                  "checksumUrl" : "https://services.gradle.org/distributions/gradle-8.14-milestone-7-bin.zip.sha256",
                  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-8.14-milestone-7-wrapper.jar.sha256"
                }, {
                  "version" : "8.14-milestone-5",
                  "buildTime" : "20250320082137+0000",
                  "current" : false,
                  "snapshot" : false,
                  "nightly" : false,
                  "releaseNightly" : false,
                  "activeRc" : false,
                  "rcFor" : "",
                  "milestoneFor" : "8.14",
                  "broken" : false,
                  "downloadUrl" : "https://services.gradle.org/distributions/gradle-8.14-milestone-5-bin.zip",
                  "checksumUrl" : "https://services.gradle.org/distributions/gradle-8.14-milestone-5-bin.zip.sha256",
                  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-8.14-milestone-5-wrapper.jar.sha256"
                }, {
                  "version" : "8.14-milestone-4",
                  "buildTime" : "20250306073633+0000",
                  "current" : false,
                  "snapshot" : false,
                  "nightly" : false,
                  "releaseNightly" : false,
                  "activeRc" : false,
                  "rcFor" : "",
                  "milestoneFor" : "8.14",
                  "broken" : false,
                  "downloadUrl" : "https://services.gradle.org/distributions/gradle-8.14-milestone-4-bin.zip",
                  "checksumUrl" : "https://services.gradle.org/distributions/gradle-8.14-milestone-4-bin.zip.sha256",
                  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-8.14-milestone-4-wrapper.jar.sha256"
                }, {
                  "version" : "8.13",
                  "buildTime" : "20250225092214+0000",
                  "current" : false,
                  "snapshot" : false,
                  "nightly" : false,
                  "releaseNightly" : false,
                  "activeRc" : false,
                  "rcFor" : "",
                  "milestoneFor" : "",
                  "broken" : false,
                  "downloadUrl" : "https://services.gradle.org/distributions/gradle-8.13-bin.zip",
                  "checksumUrl" : "https://services.gradle.org/distributions/gradle-8.13-bin.zip.sha256",
                  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-8.13-wrapper.jar.sha256"
                }, {
                  "version" : "8.14-milestone-3",
                  "buildTime" : "20250221141330+0000",
                  "current" : false,
                  "snapshot" : false,
                  "nightly" : false,
                  "releaseNightly" : false,
                  "activeRc" : false,
                  "rcFor" : "",
                  "milestoneFor" : "8.14",
                  "broken" : false,
                  "downloadUrl" : "https://services.gradle.org/distributions/gradle-8.14-milestone-3-bin.zip",
                  "checksumUrl" : "https://services.gradle.org/distributions/gradle-8.14-milestone-3-bin.zip.sha256",
                  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-8.14-milestone-3-wrapper.jar.sha256"
                }, {
                  "version" : "8.13-rc-2",
                  "buildTime" : "20250220142623+0000",
                  "current" : false,
                  "snapshot" : false,
                  "nightly" : false,
                  "releaseNightly" : false,
                  "activeRc" : false,
                  "rcFor" : "8.13",
                  "milestoneFor" : "",
                  "broken" : false,
                  "downloadUrl" : "https://services.gradle.org/distributions/gradle-8.13-rc-2-bin.zip",
                  "checksumUrl" : "https://services.gradle.org/distributions/gradle-8.13-rc-2-bin.zip.sha256",
                  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-8.13-rc-2-wrapper.jar.sha256"
                }, {
                  "version" : "8.14-milestone-2",
                  "buildTime" : "20250220124058+0000",
                  "current" : false,
                  "snapshot" : false,
                  "nightly" : false,
                  "releaseNightly" : false,
                  "activeRc" : false,
                  "rcFor" : "",
                  "milestoneFor" : "8.14",
                  "broken" : false,
                  "downloadUrl" : "https://services.gradle.org/distributions/gradle-8.14-milestone-2-bin.zip",
                  "checksumUrl" : "https://services.gradle.org/distributions/gradle-8.14-milestone-2-bin.zip.sha256",
                  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-8.14-milestone-2-wrapper.jar.sha256"
                }, {
                  "version" : "8.14-milestone-1",
                  "buildTime" : "20250214133101+0000",
                  "current" : false,
                  "snapshot" : false,
                  "nightly" : false,
                  "releaseNightly" : false,
                  "activeRc" : false,
                  "rcFor" : "",
                  "milestoneFor" : "8.14",
                  "broken" : false,
                  "downloadUrl" : "https://services.gradle.org/distributions/gradle-8.14-milestone-1-bin.zip",
                  "checksumUrl" : "https://services.gradle.org/distributions/gradle-8.14-milestone-1-bin.zip.sha256",
                  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-8.14-milestone-1-wrapper.jar.sha256"
                }, {
                  "version" : "8.13-rc-1",
                  "buildTime" : "20250212094604+0000",
                  "current" : false,
                  "snapshot" : false,
                  "nightly" : false,
                  "releaseNightly" : false,
                  "activeRc" : false,
                  "rcFor" : "8.13",
                  "milestoneFor" : "",
                  "broken" : false,
                  "downloadUrl" : "https://services.gradle.org/distributions/gradle-8.13-rc-1-bin.zip",
                  "checksumUrl" : "https://services.gradle.org/distributions/gradle-8.13-rc-1-bin.zip.sha256",
                  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-8.13-rc-1-wrapper.jar.sha256"
                }, {
                  "version" : "8.12.1",
                  "buildTime" : "20250124125512+0000",
                  "current" : false,
                  "snapshot" : false,
                  "nightly" : false,
                  "releaseNightly" : false,
                  "activeRc" : false,
                  "rcFor" : "",
                  "milestoneFor" : "",
                  "broken" : false,
                  "downloadUrl" : "https://services.gradle.org/distributions/gradle-8.12.1-bin.zip",
                  "checksumUrl" : "https://services.gradle.org/distributions/gradle-8.12.1-bin.zip.sha256",
                  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-8.12.1-wrapper.jar.sha256"
                }, {
                  "version" : "8.13-milestone-3",
                  "buildTime" : "20250121164636+0000",
                  "current" : false,
                  "snapshot" : false,
                  "nightly" : false,
                  "releaseNightly" : false,
                  "activeRc" : false,
                  "rcFor" : "",
                  "milestoneFor" : "8.13",
                  "broken" : false,
                  "downloadUrl" : "https://services.gradle.org/distributions/gradle-8.13-milestone-3-bin.zip",
                  "checksumUrl" : "https://services.gradle.org/distributions/gradle-8.13-milestone-3-bin.zip.sha256",
                  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-8.13-milestone-3-wrapper.jar.sha256"
                }, {
                  "version" : "8.12.1-milestone-1",
                  "buildTime" : "20250121110021+0000",
                  "current" : false,
                  "snapshot" : false,
                  "nightly" : false,
                  "releaseNightly" : false,
                  "activeRc" : false,
                  "rcFor" : "",
                  "milestoneFor" : "8.12.1",
                  "broken" : false,
                  "downloadUrl" : "https://services.gradle.org/distributions/gradle-8.12.1-milestone-1-bin.zip",
                  "checksumUrl" : "https://services.gradle.org/distributions/gradle-8.12.1-milestone-1-bin.zip.sha256",
                  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-8.12.1-milestone-1-wrapper.jar.sha256"
                }, {
                  "version" : "8.13-milestone-2",
                  "buildTime" : "20250110085439+0000",
                  "current" : false,
                  "snapshot" : false,
                  "nightly" : false,
                  "releaseNightly" : false,
                  "activeRc" : false,
                  "rcFor" : "",
                  "milestoneFor" : "8.13",
                  "broken" : false,
                  "downloadUrl" : "https://services.gradle.org/distributions/gradle-8.13-milestone-2-bin.zip",
                  "checksumUrl" : "https://services.gradle.org/distributions/gradle-8.13-milestone-2-bin.zip.sha256",
                  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-8.13-milestone-2-wrapper.jar.sha256"
                }, {
                  "version" : "8.13-milestone-1",
                  "buildTime" : "20250109202014+0000",
                  "current" : false,
                  "snapshot" : false,
                  "nightly" : false,
                  "releaseNightly" : false,
                  "activeRc" : false,
                  "rcFor" : "",
                  "milestoneFor" : "8.13",
                  "broken" : false,
                  "downloadUrl" : "https://services.gradle.org/distributions/gradle-8.13-milestone-1-bin.zip",
                  "checksumUrl" : "https://services.gradle.org/distributions/gradle-8.13-milestone-1-bin.zip.sha256",
                  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-8.13-milestone-1-wrapper.jar.sha256"
                }, {
                  "version" : "8.12",
                  "buildTime" : "20241220154653+0000",
                  "current" : false,
                  "snapshot" : false,
                  "nightly" : false,
                  "releaseNightly" : false,
                  "activeRc" : false,
                  "rcFor" : "",
                  "milestoneFor" : "",
                  "broken" : false,
                  "downloadUrl" : "https://services.gradle.org/distributions/gradle-8.12-bin.zip",
                  "checksumUrl" : "https://services.gradle.org/distributions/gradle-8.12-bin.zip.sha256",
                  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-8.12-wrapper.jar.sha256"
                }, {
                  "version" : "8.12-rc-2",
                  "buildTime" : "20241217162852+0000",
                  "current" : false,
                  "snapshot" : false,
                  "nightly" : false,
                  "releaseNightly" : false,
                  "activeRc" : false,
                  "rcFor" : "8.12",
                  "milestoneFor" : "",
                  "broken" : false,
                  "downloadUrl" : "https://services.gradle.org/distributions/gradle-8.12-rc-2-bin.zip",
                  "checksumUrl" : "https://services.gradle.org/distributions/gradle-8.12-rc-2-bin.zip.sha256",
                  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-8.12-rc-2-wrapper.jar.sha256"
                }, {
                  "version" : "8.12-rc-1",
                  "buildTime" : "20241212152352+0000",
                  "current" : false,
                  "snapshot" : false,
                  "nightly" : false,
                  "releaseNightly" : false,
                  "activeRc" : false,
                  "rcFor" : "8.12",
                  "milestoneFor" : "",
                  "broken" : false,
                  "downloadUrl" : "https://services.gradle.org/distributions/gradle-8.12-rc-1-bin.zip",
                  "checksumUrl" : "https://services.gradle.org/distributions/gradle-8.12-rc-1-bin.zip.sha256",
                  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-8.12-rc-1-wrapper.jar.sha256"
                }, {
                  "version" : "8.11.1",
                  "buildTime" : "20241120165646+0000",
                  "current" : false,
                  "snapshot" : false,
                  "nightly" : false,
                  "releaseNightly" : false,
                  "activeRc" : false,
                  "rcFor" : "",
                  "milestoneFor" : "",
                  "broken" : false,
                  "downloadUrl" : "https://services.gradle.org/distributions/gradle-8.11.1-bin.zip",
                  "checksumUrl" : "https://services.gradle.org/distributions/gradle-8.11.1-bin.zip.sha256",
                  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-8.11.1-wrapper.jar.sha256"
                }, {
                  "version" : "8.11",
                  "buildTime" : "20241111135801+0000",
                  "current" : false,
                  "snapshot" : false,
                  "nightly" : false,
                  "releaseNightly" : false,
                  "activeRc" : false,
                  "rcFor" : "",
                  "milestoneFor" : "",
                  "broken" : false,
                  "downloadUrl" : "https://services.gradle.org/distributions/gradle-8.11-bin.zip",
                  "checksumUrl" : "https://services.gradle.org/distributions/gradle-8.11-bin.zip.sha256",
                  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-8.11-wrapper.jar.sha256"
                }, {
                  "version" : "8.11-rc-3",
                  "buildTime" : "20241107134628+0000",
                  "current" : false,
                  "snapshot" : false,
                  "nightly" : false,
                  "releaseNightly" : false,
                  "activeRc" : false,
                  "rcFor" : "8.11",
                  "milestoneFor" : "",
                  "broken" : false,
                  "downloadUrl" : "https://services.gradle.org/distributions/gradle-8.11-rc-3-bin.zip",
                  "checksumUrl" : "https://services.gradle.org/distributions/gradle-8.11-rc-3-bin.zip.sha256",
                  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-8.11-rc-3-wrapper.jar.sha256"
                }, {
                  "version" : "8.11-rc-2",
                  "buildTime" : "20241031150259+0000",
                  "current" : false,
                  "snapshot" : false,
                  "nightly" : false,
                  "releaseNightly" : false,
                  "activeRc" : false,
                  "rcFor" : "8.11",
                  "milestoneFor" : "",
                  "broken" : false,
                  "downloadUrl" : "https://services.gradle.org/distributions/gradle-8.11-rc-2-bin.zip",
                  "checksumUrl" : "https://services.gradle.org/distributions/gradle-8.11-rc-2-bin.zip.sha256",
                  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-8.11-rc-2-wrapper.jar.sha256"
                }, {
                  "version" : "8.11-rc-1",
                  "buildTime" : "20241017104024+0000",
                  "current" : false,
                  "snapshot" : false,
                  "nightly" : false,
                  "releaseNightly" : false,
                  "activeRc" : false,
                  "rcFor" : "8.11",
                  "milestoneFor" : "",
                  "broken" : false,
                  "downloadUrl" : "https://services.gradle.org/distributions/gradle-8.11-rc-1-bin.zip",
                  "checksumUrl" : "https://services.gradle.org/distributions/gradle-8.11-rc-1-bin.zip.sha256",
                  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-8.11-rc-1-wrapper.jar.sha256"
                }, {
                  "version" : "8.11-milestone-1",
                  "buildTime" : "20241006083426+0000",
                  "current" : false,
                  "snapshot" : false,
                  "nightly" : false,
                  "releaseNightly" : false,
                  "activeRc" : false,
                  "rcFor" : "",
                  "milestoneFor" : "8.11",
                  "broken" : false,
                  "downloadUrl" : "https://services.gradle.org/distributions/gradle-8.11-milestone-1-bin.zip",
                  "checksumUrl" : "https://services.gradle.org/distributions/gradle-8.11-milestone-1-bin.zip.sha256",
                  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-8.11-milestone-1-wrapper.jar.sha256"
                }, {
                  "version" : "8.10.2",
                  "buildTime" : "20240923212839+0000",
                  "current" : false,
                  "snapshot" : false,
                  "nightly" : false,
                  "releaseNightly" : false,
                  "activeRc" : false,
                  "rcFor" : "",
                  "milestoneFor" : "",
                  "broken" : false,
                  "downloadUrl" : "https://services.gradle.org/distributions/gradle-8.10.2-bin.zip",
                  "checksumUrl" : "https://services.gradle.org/distributions/gradle-8.10.2-bin.zip.sha256",
                  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-8.10.2-wrapper.jar.sha256"
                }, {
                  "version" : "8.10.2-milestone-1",
                  "buildTime" : "20240919234735+0000",
                  "current" : false,
                  "snapshot" : false,
                  "nightly" : false,
                  "releaseNightly" : false,
                  "activeRc" : false,
                  "rcFor" : "",
                  "milestoneFor" : "8.10.2",
                  "broken" : false,
                  "downloadUrl" : "https://services.gradle.org/distributions/gradle-8.10.2-milestone-1-bin.zip",
                  "checksumUrl" : "https://services.gradle.org/distributions/gradle-8.10.2-milestone-1-bin.zip.sha256",
                  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-8.10.2-milestone-1-wrapper.jar.sha256"
                }, {
                  "version" : "8.10.1",
                  "buildTime" : "20240909074256+0000",
                  "current" : false,
                  "snapshot" : false,
                  "nightly" : false,
                  "releaseNightly" : false,
                  "activeRc" : false,
                  "rcFor" : "",
                  "milestoneFor" : "",
                  "broken" : false,
                  "downloadUrl" : "https://services.gradle.org/distributions/gradle-8.10.1-bin.zip",
                  "checksumUrl" : "https://services.gradle.org/distributions/gradle-8.10.1-bin.zip.sha256",
                  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-8.10.1-wrapper.jar.sha256"
                }, {
                  "version" : "8.10",
                  "buildTime" : "20240814110745+0000",
                  "current" : false,
                  "snapshot" : false,
                  "nightly" : false,
                  "releaseNightly" : false,
                  "activeRc" : false,
                  "rcFor" : "",
                  "milestoneFor" : "",
                  "broken" : false,
                  "downloadUrl" : "https://services.gradle.org/distributions/gradle-8.10-bin.zip",
                  "checksumUrl" : "https://services.gradle.org/distributions/gradle-8.10-bin.zip.sha256",
                  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-8.10-wrapper.jar.sha256"
                }, {
                  "version" : "8.10-rc-1",
                  "buildTime" : "20240808060755+0000",
                  "current" : false,
                  "snapshot" : false,
                  "nightly" : false,
                  "releaseNightly" : false,
                  "activeRc" : false,
                  "rcFor" : "8.10",
                  "milestoneFor" : "",
                  "broken" : false,
                  "downloadUrl" : "https://services.gradle.org/distributions/gradle-8.10-rc-1-bin.zip",
                  "checksumUrl" : "https://services.gradle.org/distributions/gradle-8.10-rc-1-bin.zip.sha256",
                  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-8.10-rc-1-wrapper.jar.sha256"
                }, {
                  "version" : "8.9",
                  "buildTime" : "20240711143741+0000",
                  "current" : false,
                  "snapshot" : false,
                  "nightly" : false,
                  "releaseNightly" : false,
                  "activeRc" : false,
                  "rcFor" : "",
                  "milestoneFor" : "",
                  "broken" : false,
                  "downloadUrl" : "https://services.gradle.org/distributions/gradle-8.9-bin.zip",
                  "checksumUrl" : "https://services.gradle.org/distributions/gradle-8.9-bin.zip.sha256",
                  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-8.9-wrapper.jar.sha256"
                }
            ]
        """.trimIndent()
    }
}