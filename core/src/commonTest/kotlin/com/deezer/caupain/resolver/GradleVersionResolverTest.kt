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
import com.deezer.caupain.model.GradleConfiguration
import com.deezer.caupain.model.Logger
import com.deezer.caupain.model.gradle.GradleStabilityLevel
import com.deezer.caupain.serialization.DefaultJson
import dev.mokkery.MockMode
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
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
        TestInfo(GradleStabilityLevel.STABLE, "9.6.1"),
        TestInfo(GradleStabilityLevel.MILESTONE, "9.7.0-milestone-3"),
        TestInfo(GradleStabilityLevel.RC, "9.6.1"),
        TestInfo(GradleStabilityLevel.RELEASE_NIGHTLY, "9.7.1-20260814014720+0000"),
        TestInfo(GradleStabilityLevel.NIGHTLY, "9.8.0-20260814002830+0000"),
    ),
    private val useGlobalVersionUrl: Boolean = burstValues(true, false),
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
            if (hasError) throw TestException()
            val url = requestData.url
            if (url == GLOBAL_VERSION_URL) {
                respond(
                    content = GRADLE_RELEASES,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                val lastSegments = url.segments.takeLast(2)
                if (lastSegments[0] == "versions") {
                    val stabilityLevel = GradleStabilityLevel
                        .entries
                        .firstOrNull { it.urlSuffix == lastSegments[1] }
                    val content = when (stabilityLevel) {
                        GradleStabilityLevel.STABLE -> GRADLE_CURRENT
                        GradleStabilityLevel.MILESTONE -> GRADLE_MILESTONE
                        GradleStabilityLevel.RC -> GRADLE_RC
                        GradleStabilityLevel.RELEASE_NIGHTLY -> GRADLE_RELEASE_NIGHTLY
                        GradleStabilityLevel.NIGHTLY -> GRADLE_NIGHTLY
                        else -> null
                    }
                    if (content == null) {
                        respond("Not found", HttpStatusCode.NotFound)
                    } else {
                        respond(
                            content = content,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }
                } else {
                    respond("Not found", HttpStatusCode.NotFound)
                }
            }
        }
        resolver = GradleVersionResolver(
            httpClient = HttpClient(engine) {
                install(ContentNegotiation) {
                    json(DefaultJson, ContentType.Application.Json)
                }
            },
            logger = logger,
            configuration = GradleConfiguration(
                version = "8.12",
                globalVersionsUrl = if (useGlobalVersionUrl) GLOBAL_VERSION_URL else null,
            ),
            stabilityLevel = testInfo.stabilityLevel,
            ioDispatcher = testDispatcher
        )
    }

    @AfterTest
    fun teardown() {
        engine.close()
        hasError = false
    }

    @Test
    fun testUpdate() = runTest(testDispatcher) {
        assertEquals(
            expected = testInfo.expectedVersion,
            actual = resolver.getUpdatedVersion()?.updatedVersion?.version
        )
    }

    @Test
    fun testUpdateError() = runTest(testDispatcher) {
        hasError = true
        assertNull(resolver.getUpdatedVersion())
        verify {
            logger.error(message = any(), throwable = any<TestException>())
        }
    }

    data class TestInfo(
        val stabilityLevel: GradleStabilityLevel,
        val expectedVersion: String?
    ) {
        override fun toString(): String = stabilityLevel.name
    }

    private class TestException : IOException()

    @Suppress("LargeClass")
    companion object {
        private val GLOBAL_VERSION_URL = Url("https://services.gradle.org/versions/all")

        @Language("JSON")
        private val GRADLE_RELEASES = """
            [
  {
    "version": "9.7.1-20260814014720+0000",
    "buildTime": "20260814014720+0000",
    "commitId": "41cfceec99c65a62ad18a3299ff17e2947238c2c",
    "current": false,
    "snapshot": true,
    "nightly": false,
    "releaseNightly": true,
    "activeRc": false,
    "rcFor": "",
    "milestoneFor": "",
    "broken": false,
    "downloadUrl": "https://services.gradle.org/distributions-snapshots/gradle-9.7.1-20260814014720+0000-bin.zip",
    "checksumUrl": "https://services.gradle.org/distributions-snapshots/gradle-9.7.1-20260814014720+0000-bin.zip.sha256",
    "checksum": "d25af96e14c8adf7b70463a48999706e2ecbc8f6e331f9ffbf116987e83c307f",
    "wrapperChecksumUrl": "https://services.gradle.org/distributions-snapshots/gradle-9.7.1-20260814014720+0000-wrapper.jar.sha256",
    "wrapperChecksum": "7a9ce74cff467ca1bf60a4fcd9f05185acceda4d0f382434d393e17864262c5d",
    "released": true,
    "releasedOrActiveRc": true,
    "publicationSlot": "release-nightly",
    "final": false
  },
  {
    "version": "9.8.0-20260814002830+0000",
    "buildTime": "20260814002830+0000",
    "commitId": "c866f4056666340bd027d3e2723ed90660cf7049",
    "current": false,
    "snapshot": true,
    "nightly": true,
    "releaseNightly": false,
    "activeRc": false,
    "rcFor": "",
    "milestoneFor": "",
    "broken": false,
    "downloadUrl": "https://services.gradle.org/distributions-snapshots/gradle-9.8.0-20260814002830+0000-bin.zip",
    "checksumUrl": "https://services.gradle.org/distributions-snapshots/gradle-9.8.0-20260814002830+0000-bin.zip.sha256",
    "checksum": "be5b316563b46c8fad2162950a159162e10dee0fbca60e8d9a545e2b7d6756b5",
    "wrapperChecksumUrl": "https://services.gradle.org/distributions-snapshots/gradle-9.8.0-20260814002830+0000-wrapper.jar.sha256",
    "wrapperChecksum": "2b2e2cee3d8a8e5379b4f1c5902419404e83c1dba5ff55192ad5986e3f44cd6e",
    "released": false,
    "releasedOrActiveRc": false,
    "publicationSlot": "nightly",
    "final": false
  },
  {
    "version": "9.7.0-milestone-3",
    "buildTime": "20260710170428+0000",
    "commitId": "652e81fe0294495edb268b7b5a5c82ebb21f16e5",
    "current": false,
    "snapshot": false,
    "nightly": false,
    "releaseNightly": false,
    "activeRc": false,
    "rcFor": "",
    "milestoneFor": "9.7.0",
    "broken": false,
    "downloadUrl": "https://services.gradle.org/distributions/gradle-9.7.0-milestone-3-bin.zip",
    "checksumUrl": "https://services.gradle.org/distributions/gradle-9.7.0-milestone-3-bin.zip.sha256",
    "checksum": "a929bbcb295cb360d04685adaf3a6af16bf1d201fc4dbc91c714c7e12d9b1abe",
    "wrapperChecksumUrl": "https://services.gradle.org/distributions/gradle-9.7.0-milestone-3-wrapper.jar.sha256",
    "wrapperChecksum": "7a9ce74cff467ca1bf60a4fcd9f05185acceda4d0f382434d393e17864262c5d",
    "released": true,
    "releasedOrActiveRc": true,
    "publicationSlot": "9.7.0-milestone-3",
    "final": false
  },
  {
    "version": "9.7.0-milestone-2",
    "buildTime": "20260703081850+0000",
    "commitId": "0e59e8c17f6d27809073ac79dcc4fd444d64e023",
    "current": false,
    "snapshot": false,
    "nightly": false,
    "releaseNightly": false,
    "activeRc": false,
    "rcFor": "",
    "milestoneFor": "9.7.0",
    "broken": false,
    "downloadUrl": "https://services.gradle.org/distributions/gradle-9.7.0-milestone-2-bin.zip",
    "checksumUrl": "https://services.gradle.org/distributions/gradle-9.7.0-milestone-2-bin.zip.sha256",
    "checksum": "422c9fb7a796bf418fe4dcc56bf653af2cebe9ca8f02cf0c26f62aae9cdd0c5c",
    "wrapperChecksumUrl": "https://services.gradle.org/distributions/gradle-9.7.0-milestone-2-wrapper.jar.sha256",
    "wrapperChecksum": "7a9ce74cff467ca1bf60a4fcd9f05185acceda4d0f382434d393e17864262c5d",
    "released": true,
    "releasedOrActiveRc": true,
    "publicationSlot": "9.7.0-milestone-2",
    "final": false
  },
  {
    "version" : "9.6.1",
    "buildTime" : "20260626142550+0000",
    "commitId" : "309d128bd9fe8c0b71311878fc660b9cbaa07c51",
    "current" : false,
    "snapshot" : false,
    "nightly" : false,
    "releaseNightly" : false,
    "activeRc" : false,
    "rcFor" : "",
    "milestoneFor" : "",
    "broken" : false,
    "downloadUrl" : "https://services.gradle.org/distributions/gradle-9.6.1-bin.zip",
    "checksumUrl" : "https://services.gradle.org/distributions/gradle-9.6.1-bin.zip.sha256",
    "checksum" : "9c0f7faeeb306cb14e4279a3e084ca6b596894089a0638e68a07c945a32c9e14",
    "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-9.6.1-wrapper.jar.sha256",
    "wrapperChecksum" : "497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7",
    "released" : true,
    "releasedOrActiveRc" : true,
    "publicationSlot" : "9.6.1",
    "final" : true
  }
]
        """.trimIndent()

        const val GRADLE_CURRENT = """
        {
  "version" : "9.6.1",
  "buildTime" : "20260626142550+0000",
  "commitId" : "309d128bd9fe8c0b71311878fc660b9cbaa07c51",
  "current" : false,
  "snapshot" : false,
  "nightly" : false,
  "releaseNightly" : false,
  "activeRc" : false,
  "rcFor" : "",
  "milestoneFor" : "",
  "broken" : false,
  "downloadUrl" : "https://services.gradle.org/distributions/gradle-9.6.1-bin.zip",
  "checksumUrl" : "https://services.gradle.org/distributions/gradle-9.6.1-bin.zip.sha256",
  "checksum" : "9c0f7faeeb306cb14e4279a3e084ca6b596894089a0638e68a07c945a32c9e14",
  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-9.6.1-wrapper.jar.sha256",
  "wrapperChecksum" : "497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7",
  "released" : true,
  "releasedOrActiveRc" : true,
  "publicationSlot" : "9.6.1",
  "final" : true
}
    """

        private const val GRADLE_RC = "{}"

        private const val GRADLE_MILESTONE = """
        {
  "version" : "9.7.0-milestone-3",
  "buildTime" : "20260710170428+0000",
  "commitId" : "652e81fe0294495edb268b7b5a5c82ebb21f16e5",
  "current" : false,
  "snapshot" : false,
  "nightly" : false,
  "releaseNightly" : false,
  "activeRc" : false,
  "rcFor" : "",
  "milestoneFor" : "9.7.0",
  "broken" : false,
  "downloadUrl" : "https://services.gradle.org/distributions/gradle-9.7.0-milestone-3-bin.zip",
  "checksumUrl" : "https://services.gradle.org/distributions/gradle-9.7.0-milestone-3-bin.zip.sha256",
  "checksum" : "a929bbcb295cb360d04685adaf3a6af16bf1d201fc4dbc91c714c7e12d9b1abe",
  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-9.7.0-milestone-3-wrapper.jar.sha256",
  "wrapperChecksum" : "7a9ce74cff467ca1bf60a4fcd9f05185acceda4d0f382434d393e17864262c5d",
  "released" : true,
  "releasedOrActiveRc" : true,
  "publicationSlot" : "9.7.0-milestone-3",
  "final" : false
}    
        """

        private const val GRADLE_NIGHTLY = """
            {
  "version" : "9.8.0-20260814002830+0000",
  "buildTime" : "20260814002830+0000",
  "commitId" : "c866f4056666340bd027d3e2723ed90660cf7049",
  "current" : false,
  "snapshot" : true,
  "nightly" : true,
  "releaseNightly" : false,
  "activeRc" : false,
  "rcFor" : "",
  "milestoneFor" : "",
  "broken" : false,
  "downloadUrl" : "https://services.gradle.org/distributions-snapshots/gradle-9.8.0-20260814002830+0000-bin.zip",
  "checksumUrl" : "https://services.gradle.org/distributions-snapshots/gradle-9.8.0-20260814002830+0000-bin.zip.sha256",
  "checksum" : "be5b316563b46c8fad2162950a159162e10dee0fbca60e8d9a545e2b7d6756b5",
  "wrapperChecksumUrl" : "https://services.gradle.org/distributions-snapshots/gradle-9.8.0-20260814002830+0000-wrapper.jar.sha256",
  "wrapperChecksum" : "2b2e2cee3d8a8e5379b4f1c5902419404e83c1dba5ff55192ad5986e3f44cd6e",
  "released" : false,
  "releasedOrActiveRc" : false,
  "publicationSlot" : "nightly",
  "final" : false
}
        """

        private const val GRADLE_RELEASE_NIGHTLY = """
            {
  "version" : "9.7.1-20260814014720+0000",
  "buildTime" : "20260814014720+0000",
  "commitId" : "41cfceec99c65a62ad18a3299ff17e2947238c2c",
  "current" : false,
  "snapshot" : true,
  "nightly" : false,
  "releaseNightly" : true,
  "activeRc" : false,
  "rcFor" : "",
  "milestoneFor" : "",
  "broken" : false,
  "downloadUrl" : "https://services.gradle.org/distributions-snapshots/gradle-9.7.1-20260814014720+0000-bin.zip",
  "checksumUrl" : "https://services.gradle.org/distributions-snapshots/gradle-9.7.1-20260814014720+0000-bin.zip.sha256",
  "checksum" : "d25af96e14c8adf7b70463a48999706e2ecbc8f6e331f9ffbf116987e83c307f",
  "wrapperChecksumUrl" : "https://services.gradle.org/distributions-snapshots/gradle-9.7.1-20260814014720+0000-wrapper.jar.sha256",
  "wrapperChecksum" : "7a9ce74cff467ca1bf60a4fcd9f05185acceda4d0f382434d393e17864262c5d",
  "released" : true,
  "releasedOrActiveRc" : true,
  "publicationSlot" : "release-nightly",
  "final" : false
}
        """
    }
}
