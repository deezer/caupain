package com.deezer.dependencies.internal

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
public fun FileStorage(
    fileSystem: FileSystem,
    directory: Path,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
): CacheStorage = CachingCacheStorage(FileCacheStorage(fileSystem, directory, dispatcher))

private val LOGGER = KtorSimpleLogger("io.ktor.client.plugins.HttpCache")

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
        return data.find {
            varyKeys.all { (key, value) -> it.varyKeys[key] == value } && varyKeys.size == it.varyKeys.size
        }
    }

    override suspend fun findAll(url: Url): Set<CachedResponseData> {
        if (!store.containsKey(url)) {
            store[url] = delegate.findAll(url)
        }
        return store.getValue(url)
    }
}

private class FileCacheStorage(
    private val fileSystem: FileSystem,
    private val directory: Path,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : CacheStorage {

    private val mutexes = ConcurrentMutableMap<String, Mutex>()

    init {
        fileSystem.createDirectories(directory)
    }

    override suspend fun store(url: Url, data: CachedResponseData): Unit = withContext(dispatcher) {
        val urlHex = key(url)
        val caches = readCache(urlHex).filterNot { it.varyKeys == data.varyKeys } + data
        writeCache(urlHex, caches)
    }

    override suspend fun findAll(url: Url): Set<CachedResponseData> {
        return readCache(key(url)).toSet()
    }

    override suspend fun find(url: Url, varyKeys: Map<String, String>): CachedResponseData? {
        val data = readCache(key(url))
        return data.find {
            varyKeys.all { (key, value) -> it.varyKeys[key] == value } && varyKeys.size == it.varyKeys.size
        }
    }

    private fun key(url: Url): String = url.toString().toSHA256Hex()

    private suspend fun writeCache(urlHex: String, caches: List<CachedResponseData>) {
        val mutex = mutexes.computeIfAbsent(urlHex) { Mutex() }
        mutex.withLock {
            try {
                withContext(dispatcher) {
                    val file = directory / urlHex
                    fileSystem.write(file) {
                        writeInt(caches.size)
                        for (cache in caches) writeCache(cache)
                    }
                }
            } catch (cause: Exception) {
                LOGGER.trace("Exception during saving a cache to a file: ${cause.stackTraceToString()}")
            }
        }
    }

    private suspend fun readCache(urlHex: String): Set<CachedResponseData> {
        val mutex = mutexes.computeIfAbsent(urlHex) { Mutex() }
        mutex.withLock {
            val file = directory / urlHex
            if (!fileSystem.exists(file)) return emptySet()

            try {
                return withContext(dispatcher) {
                    fileSystem.read(file) {
                        val requestsCount = readInt()
                        val caches = mutableSetOf<CachedResponseData>()
                        for (i in 0 until requestsCount) caches.add(readCache())
                        readByteString()
                        caches
                    }
                }
            } catch (cause: Exception) {
                LOGGER.trace("Exception during cache lookup in a file: ${cause.stackTraceToString()}")
                return emptySet()
            }
        }
    }

    private suspend fun BufferedSink.writeCache(cache: CachedResponseData) {
        withContext(dispatcher) {
            writeUtf8(cache.url.toString() + "\n")
            writeInt(cache.statusCode.value)
            writeUtf8(cache.statusCode.description + "\n")
            writeUtf8(cache.version.toString() + "\n")
            val headers = cache.headers.flattenEntries()
            writeInt(headers.size)
            for ((key, value) in headers) {
                writeUtf8(key + "\n")
                writeUtf8(value + "\n")
            }
            writeLong(cache.requestTime.timestamp)
            writeLong(cache.responseTime.timestamp)
            writeLong(cache.expires.timestamp)
            writeInt(cache.varyKeys.size)
            for ((key, value) in cache.varyKeys) {
                writeUtf8(key + "\n")
                writeUtf8(value + "\n")
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
            for (j in 0 until headersCount) {
                val key = readUtf8Line()!!
                val value = readUtf8Line()!!
                headers.append(key, value)
            }
            val requestTime = GMTDate(readLong())
            val responseTime = GMTDate(readLong())
            val expirationTime = GMTDate(readLong())
            val varyKeysCount = readInt()
            val varyKeys = buildMap {
                for (j in 0 until varyKeysCount) {
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