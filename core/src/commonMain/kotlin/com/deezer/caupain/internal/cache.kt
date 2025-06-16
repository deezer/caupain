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

import co.touchlab.stately.collections.ConcurrentMutableMap
import io.ktor.client.plugins.cache.storage.CacheStorage
import io.ktor.client.plugins.cache.storage.CachedResponseData
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.util.date.GMTDate
import io.ktor.util.flattenEntries
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.BufferedSink
import okio.BufferedSource
import okio.FileSystem
import okio.Path

@Suppress("FunctionName")
internal fun FileStorage(
    fileSystem: FileSystem,
    directory: Path,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
): CacheStorage = CachingCacheStorage(FileCacheStorage(fileSystem, directory, dispatcher))

private val LOGGER = KtorSimpleLogger("io.ktor.client.plugins.HttpCache")

// This is adapted from the JVM file in ktor
private class CachingCacheStorage(
    private val delegate: CacheStorage
) : CacheStorage {

    private val store = ConcurrentMutableMap<Url, Set<CachedResponseData>>()

    override suspend fun store(url: Url, data: CachedResponseData) {
        delegate.store(url, data)
        store[url] = delegate.findAll(url)
    }

    override suspend fun find(url: Url, varyKeys: Map<String, String>): CachedResponseData? {
        if (!store.containsKey(url)) {
            store[url] = delegate.findAll(url)
        }
        val data = store.getValue(url)
        return data.firstOrNull {
            varyKeys.all { (key, value) -> it.varyKeys[key] == value } && varyKeys.size == it.varyKeys.size
        }
    }

    override suspend fun findAll(url: Url): Set<CachedResponseData> {
        if (!store.containsKey(url)) {
            store[url] = delegate.findAll(url)
        }
        return store.getValue(url)
    }

    override suspend fun remove(
        url: Url,
        varyKeys: Map<String, String>
    ) {
        delegate.remove(url, varyKeys)
        store[url] = delegate.findAll(url)
    }

    override suspend fun removeAll(url: Url) {
        delegate.removeAll(url)
        store.remove(url)
    }
}

@Suppress("TooManyFunctions")
private class FileCacheStorage(
    private val fileSystem: FileSystem,
    private val directory: Path,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : CacheStorage {

    private val mutexes = ConcurrentMutableMap<String, Mutex>()

    init {
        fileSystem.createDirectories(directory)
    }

    override suspend fun store(url: Url, data: CachedResponseData) {
        withContext(dispatcher) {
            val urlHex = key(url)
            updateCache(urlHex) { caches ->
                caches.filterNot { it.varyKeys == data.varyKeys } + data
            }
        }
    }

    override suspend fun findAll(url: Url): Set<CachedResponseData> {
        return readCache(key(url)).toSet()
    }

    override suspend fun find(url: Url, varyKeys: Map<String, String>): CachedResponseData? {
        val data = readCache(key(url))
        return data.firstOrNull {
            varyKeys.all { (key, value) -> it.varyKeys[key] == value } && varyKeys.size == it.varyKeys.size
        }
    }

    override suspend fun remove(url: Url, varyKeys: Map<String, String>) {
        val urlHex = key(url)
        updateCache(urlHex) { caches ->
            caches.filterNot { it.varyKeys == varyKeys }
        }
    }

    override suspend fun removeAll(url: Url) {
        val urlHex = key(url)
        deleteCache(urlHex)
    }

    private fun key(url: Url): String = url.toString().toSHA256Hex()

    private suspend fun readCache(urlHex: String): Set<CachedResponseData> {
        val mutex = mutexes.computeIfAbsent(urlHex) { Mutex() }
        return mutex.withLock { readCacheUnsafe(urlHex) }
    }

    private suspend inline fun updateCache(
        urlHex: String,
        transform: (Set<CachedResponseData>) -> List<CachedResponseData>
    ) {
        val mutex = mutexes.computeIfAbsent(urlHex) { Mutex() }
        mutex.withLock {
            val caches = readCacheUnsafe(urlHex)
            writeCacheUnsafe(urlHex, transform(caches))
        }
    }

    private suspend fun deleteCache(urlHex: String) {
        val mutex = mutexes.computeIfAbsent(urlHex) { Mutex() }
        mutex.withLock {
            val file = directory / urlHex
            if (!fileSystem.exists(file)) return@withLock

            try {
                withContext(dispatcher) {
                    fileSystem.delete(file)
                }
            } catch (ignored: Exception) {
                LOGGER.trace("Exception during cache deletion in a file: ${ignored.stackTraceToString()}")
            }
        }
    }

    private suspend fun writeCacheUnsafe(urlHex: String, caches: List<CachedResponseData>) {
        try {
            withContext(dispatcher) {
                val file = directory / urlHex
                fileSystem.write(file) {
                    writeInt(caches.size)
                    for (cache in caches) writeCache(cache)
                }
            }
        } catch (ignored: Exception) {
            LOGGER.trace("Exception during saving a cache to a file: ${ignored.stackTraceToString()}")
        }
    }


    private suspend fun readCacheUnsafe(urlHex: String): Set<CachedResponseData> {
        val file = directory / urlHex
        if (!fileSystem.exists(file)) return emptySet()

        try {
            return withContext(dispatcher) {
                fileSystem.read(file) {
                    val requestsCount = readInt()
                    val caches = mutableSetOf<CachedResponseData>()
                    repeat(requestsCount) { caches.add(readCache()) }
                    readByteString()
                    caches
                }
            }
        } catch (ignored: Exception) {
            LOGGER.trace("Exception during cache lookup in a file: ${ignored.stackTraceToString()}")
            return emptySet()
        }
    }

    private suspend fun BufferedSink.writeCache(cache: CachedResponseData) {
        withContext(dispatcher) {
            writeUtf8Line(cache.url.toString())
            writeInt(cache.statusCode.value)
            writeUtf8Line(cache.statusCode.description)
            writeUtf8Line(cache.version.toString())
            val headers = cache.headers.flattenEntries()
            writeInt(headers.size)
            for ((key, value) in headers) {
                writeUtf8Line(key)
                writeUtf8Line(value)
            }
            writeLong(cache.requestTime.timestamp)
            writeLong(cache.responseTime.timestamp)
            writeLong(cache.expires.timestamp)
            writeInt(cache.varyKeys.size)
            for ((key, value) in cache.varyKeys) {
                writeUtf8Line(key)
                writeUtf8Line(value)
            }
            writeInt(cache.body.size)
            write(cache.body)
        }
    }

    private suspend fun BufferedSource.readCache(): CachedResponseData {
        return withContext(dispatcher) {
            val url = readUtf8Line()!!
            val status = HttpStatusCode(readInt(), readUtf8Line()!!)
            val version = HttpProtocolVersion.parse(readUtf8Line()!!)
            val headersCount = readInt()
            val headers = HeadersBuilder()
            repeat(headersCount) {
                val key = readUtf8Line()!!
                val value = readUtf8Line()!!
                headers.append(key, value)
            }
            val requestTime = GMTDate(readLong())
            val responseTime = GMTDate(readLong())
            val expirationTime = GMTDate(readLong())
            val varyKeysCount = readInt()
            val varyKeys = buildMap {
                repeat(varyKeysCount) {
                    val key = readUtf8Line()!!
                    val value = readUtf8Line()!!
                    put(key, value)
                }
            }
            val bodyCount = readInt()
            val body = ByteArray(bodyCount)
            readFully(body)
            CachedResponseData(
                url = Url(url),
                statusCode = status,
                requestTime = requestTime,
                responseTime = responseTime,
                version = version,
                expires = expirationTime,
                headers = headers.build(),
                varyKeys = varyKeys,
                body = body
            )
        }
    }
}

private fun BufferedSink.writeUtf8Line(line: String) {
    writeUtf8(line)
    writeUtf8CodePoint('\n'.code)
}