package com.deezer.caupain.resolver

import com.deezer.caupain.model.GradleVersion
import com.deezer.caupain.serialization.DefaultJson
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class GradleVersionResolverTest {
    private lateinit var engine: MockEngine

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var resolver: GradleVersionResolver

    @BeforeTest
    fun setup() {
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
            gradleCurrentVersionUrl = GRADLE_VERSIONS_URL.toString(),
            ioDispatcher = testDispatcher
        )
    }

    private fun handleRequest(
        scope: MockRequestHandleScope,
        requestData: HttpRequestData
    ): HttpResponseData? {
        val url = requestData.url
        return if (url == GRADLE_VERSIONS_URL) {
            scope.respond(
                content = DefaultJson.encodeToString(GradleVersion("8.13")),
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        } else {
            null
        }
    }

    @AfterTest
    fun teardown() {
        engine.close()
    }

    @Test
    fun testUpdate() = runTest(testDispatcher) {
        assertEquals("8.13", resolver.getUpdatedVersion("8.11"))
        assertNull(resolver.getUpdatedVersion("8.14"))
    }

    companion object {
        private val BASE_URL = Url("http://www.example.com")

        private val GRADLE_VERSIONS_URL = URLBuilder()
            .takeFrom(BASE_URL)
            .appendPathSegments("gradle", "version.json")
            .build()
    }
}