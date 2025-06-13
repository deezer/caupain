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

package com.deezer.caupain.model

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RepositoryTest {

    private lateinit var engine: MockEngine

    private lateinit var client: HttpClient

    @BeforeTest
    fun setup() {
        engine = MockEngine { respond("OK") }
        client = HttpClient(engine)
    }

    private inline fun checkRequest(
        repository: Repository,
        crossinline check: HttpRequestData.() -> Unit
    ) {
        runTest {
            client.executeRepositoryRequest(repository)
            engine.requestHistory.last().check()
        }
    }

    @Test
    fun testSimpleRepository() {
        checkRequest(Repository("http://www.example.com")) {
            assertEquals(2, headers.entries().size)
        }
    }

    @Test
    fun testPasswordRepository() {
        checkRequest(Repository("http://www.example.com", "user", "password")) {
            assertEquals(
                expected = "Basic dXNlcjpwYXNzd29yZA==",
                actual = headers[HttpHeaders.Authorization]
            )
        }
    }

    @Test
    fun testHeadersRepository() {
        checkRequest(
            Repository(
                "http://www.example.com",
                HeaderCredentials("X-Specific", "Value")
            )
        ) {
            assertEquals(
                expected = "Value",
                actual = headers["X-Specific"]
            )
        }
    }
}