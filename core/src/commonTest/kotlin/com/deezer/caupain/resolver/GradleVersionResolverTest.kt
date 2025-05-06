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