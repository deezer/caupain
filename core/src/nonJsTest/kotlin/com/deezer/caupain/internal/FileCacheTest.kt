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
 *
 * This file incorporates work covered by the following copyright and permission notice:
 *
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the
 * Apache 2.0 license.
 */

package com.deezer.caupain.internal

import com.deezer.caupain.AfterClass
import com.deezer.caupain.BeforeClass
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.cache.storage.CacheStorage
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.content.CachingOptions
import io.ktor.http.content.LastModifiedVersion
import io.ktor.http.content.TextContent
import io.ktor.http.content.caching
import io.ktor.http.content.versions
import io.ktor.server.application.Application
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.cachingheaders.CachingHeaders
import io.ktor.server.plugins.conditionalheaders.ConditionalHeaders
import io.ktor.server.request.header
import io.ktor.server.response.cacheControl
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.util.date.GMTDate
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.jvm.JvmStatic
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO

@OptIn(ExperimentalCoroutinesApi::class)
class FileCacheTest {

    private lateinit var fileSystem: FakeFileSystem

    private lateinit var publicStorage: CacheStorage

    private lateinit var privateStorage: CacheStorage

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setup() {
        fileSystem = FakeFileSystem()
        val publicPath = "cache-test-public".toPath()
        fileSystem.createDirectories(publicPath)
        publicStorage = FileStorage(fileSystem, publicPath, testDispatcher)
        val privatePath = "cache-test-private".toPath()
        fileSystem.createDirectories(privatePath)
        privateStorage = FileStorage(fileSystem, privatePath, testDispatcher)
    }

    @AfterTest
    fun teardown() {
        fileSystem.checkNoOpenFiles()
        fileSystem.close()
    }

    private inline fun testOnClient(
        crossinline config: HttpClientConfig<*>.() -> Unit,
        crossinline test: suspend (HttpClient) -> Unit
    ) {
        val client = HttpClient(ClientCIO) {
            config()
        }
        runTest(testDispatcher) {
            test(client)
        }
    }

    @Test
    fun testVaryHeader() {
        testOnClient(
            config = {
                install(HttpCache) {
                    publicStorage(this@FileCacheTest.publicStorage)
                    privateStorage(this@FileCacheTest.privateStorage)
                }
            },
        ) { client ->
            val url = Url("$TEST_SERVER/cache/vary")

            // first header value from Vary
            val first = client.get(url) {
                header(HttpHeaders.ContentLanguage, "en")
            }.body<String>()

            val second = client.get(url) {
                header(HttpHeaders.ContentLanguage, "en")
            }.body<String>()

            assertEquals(first, second)

            // second header value from Vary
            val third = client.get(url) {
                header(HttpHeaders.ContentLanguage, "ru")
            }.body<String>()

            assertNotEquals(third, second)

            val fourth = client.get(url) {
                header(HttpHeaders.ContentLanguage, "ru")
            }.body<String>()

            assertEquals(third, fourth)

            // first header value from Vary
            val fifth = client.get(url) {
                header(HttpHeaders.ContentLanguage, "en")
            }.body<String>()

            assertEquals(first, fifth)

            // no header value from Vary
            val sixth = client.get(url).body<String>()

            assertNotEquals(sixth, second)
            assertNotEquals(sixth, third)

            val seventh = client.get(url).body<String>()

            assertEquals(sixth, seventh)

            assertEquals(3, publicStorage.findAll(url).size)
            assertEquals(0, privateStorage.findAll(url).size)
        }
    }

    @Test
    fun testReuseCacheStorage() {
        testOnClient(
            config = {
                install(HttpCache) {
                    publicStorage(this@FileCacheTest.publicStorage)
                    privateStorage(this@FileCacheTest.privateStorage)
                }
            }
        ) { client ->
            val client1 = client.config { }
            val client2 = client.config { }
            val url = Url("$TEST_SERVER/cache/etag-304")

            val first = client1.get(url)
            val second = client2.get(url)

            assertEquals(HttpStatusCode.OK, first.status)
            assertEquals(HttpStatusCode.OK, second.status)
            assertEquals(first.body<String>(), second.body<String>())
        }
    }

    @Test
    fun testLongPath() {
        testOnClient(
            config = {
                install(HttpCache) {
                    publicStorage(this@FileCacheTest.publicStorage)
                }
            }
        ) { client ->
            val response = client.get("$TEST_SERVER/cache/cache_${"a".repeat(3000)}").body<String>()
            assertEquals("abc", response)
        }
    }

    @Test
    fun testSkipCacheIfException() {
        val path = "cache-test-public-deleted".toPath()
        fileSystem.createDirectories(path)
        val publicStorage = FileStorage(fileSystem, path)
        testOnClient(
            config = {
                install(HttpCache) {
                    publicStorage(publicStorage)
                }
            }
        ) { client ->
            val first = client.get(Url("$TEST_SERVER/cache/public")).bodyAsText()
            assertEquals("public", first)

            fileSystem.deleteRecursively(path)

            val second = client.get("$TEST_SERVER/cache/cache_${"a".repeat(3000)}")
            assertEquals("abc", second.bodyAsText())
        }
    }

    companion object {
        private const val TEST_SERVER = "http://127.0.0.1:8080"

        private var server: EmbeddedServer<*, *>? = null

        private val counter = atomic(0)

        @BeforeClass
        @JvmStatic
        fun setupServer() {
            server = embeddedServer(ServerCIO, port = 8080) {
                configureRequests()
            }.start()
        }

        private fun Application.configureRequests() {
            routing {
                route("/cache") {
                    install(CachingHeaders)
                    install(ConditionalHeaders)

                    get("/etag-304") {
                        if (call.request.header("If-None-Match") == "My-ETAG") {
                            call.response.header("Etag", "My-ETAG")
                            call.response.header("Vary", "Origin, Accept-Encoding")
                            call.respond(HttpStatusCode.NotModified)
                            return@get
                        }

                        call.response.header("Etag", "My-ETAG")
                        call.response.header("Vary", "Origin, Accept-Encoding")
                        call.respondText(contentType = ContentType.Application.Json) { "{}" }
                    }

                    get("/vary") {
                        val current = counter.incrementAndGet()
                        val response = TextContent("$current", ContentType.Text.Plain).apply {
                            caching = CachingOptions(CacheControl.MaxAge(60))
                        }
                        response.versions += LastModifiedVersion(GMTDate.START)

                        call.response.header(HttpHeaders.Vary, HttpHeaders.ContentLanguage)
                        call.respond(response)
                    }

                    get("/public") {
                        call.response.cacheControl(CacheControl.MaxAge(60))
                        call.respondText("public")
                    }
                    get("/cache_${"a".repeat(3000)}") {
                        call.respondText { "abc" }
                    }
                }
            }
        }

        @AfterClass
        @JvmStatic
        fun teardownServer() {
            server?.stop(0, 0)
            server = null
        }
    }
}