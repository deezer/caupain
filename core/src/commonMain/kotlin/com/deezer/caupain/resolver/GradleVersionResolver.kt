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

import com.deezer.caupain.internal.HttpException
import com.deezer.caupain.internal.processRequest
import com.deezer.caupain.model.GradleConfiguration
import com.deezer.caupain.model.GradleDependencyVersion
import com.deezer.caupain.model.GradleUpdateInfo
import com.deezer.caupain.model.Logger
import com.deezer.caupain.model.gradle.GradleStabilityLevel
import com.deezer.caupain.model.gradle.GradleToolVersion
import com.deezer.caupain.model.gradle.GradleVersion
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

internal class GradleVersionResolver(
    private val httpClient: HttpClient,
    private val logger: Logger,
    private val configuration: GradleConfiguration,
    private val reportUpToDate: Boolean,
    private val stabilityLevel: GradleStabilityLevel,
    private val ioDispatcher: CoroutineDispatcher
) {
    // We do not use it as a scope, but HttpClient does implement scope
    @Suppress("SuspendFunWithCoroutineScopeReceiver")
    private suspend fun HttpClient.getVersions(url: Url): Result<List<GradleToolVersion>> {
        return processRequest<List<GradleToolVersion>, List<GradleToolVersion>>(
            transform = { versions ->
                versions.filter { it.version != null && it.level <= stabilityLevel }
            },
            executeRequest = { get(url) }
        ).onFailure { error ->
            if (error !is HttpException) {
                logger.error("Failed to fetch Gradle versions from $url", error)
            }
        }
    }

    // We do not use it as a scope, but HttpClient does implement scope
    @Suppress("SuspendFunWithCoroutineScopeReceiver")
    private suspend fun HttpClient.getVersion(url: Url): Result<GradleToolVersion?> {
        return processRequest<GradleToolVersion, GradleToolVersion?>(
            transform = { version ->
                version.takeIf { it.version != null && it.level <= stabilityLevel }
            },
            executeRequest = { get(url) }
        ).onFailure { error ->
            if (error !is HttpException) {
                logger.error("Failed to fetch Gradle versions from $url", error)
            }
        }
    }

    private suspend fun getLastVersions(): Result<List<GradleToolVersion>> {
        return coroutineScope {
            GradleStabilityLevel
                .entries
                .asSequence()
                .filter { it <= stabilityLevel }
                .map { level ->
                    URLBuilder(configuration.baseVersionsUrl)
                        .appendPathSegments(level.urlSuffix)
                        .build()
                }
                .map { url ->
                    async { httpClient.getVersion(url) }
                }
                .toList()
                .awaitAll()
                .let { results ->
                    if (results.any { it.isSuccess }) {
                        Result.success(results.mapNotNull { it.getOrNull() })
                    } else {
                        Result.failure(results.firstNotNullOf { it.exceptionOrNull() })
                    }
                }
        }
    }

    suspend fun getUpdatedVersion(): GradleUpdateInfo? {
        return withContext(ioDispatcher) {
            if (configuration.globalVersionsUrl != null) {
                httpClient.getVersions(configuration.globalVersionsUrl)
            } else {
                getLastVersions()
            }
        }.fold(
            onSuccess = { versions ->
                var max: GradleToolVersion? = null
                val currentVersion = GradleDependencyVersion(configuration.version)
                for (version in versions) {
                    @Suppress("ComplexCondition")
                    if (
                        version.version != null
                        && currentVersion.isUpdate(version.version)
                        && (max?.version == null || version.version > max.version)
                    ) {
                        max = version
                    }
                }
                when {
                    max != null -> GradleUpdateInfo(
                        currentVersion = GradleVersion(configuration.version),
                        updatedVersion = requireNotNull(max.toGradleVersion()),
                        checksum = if (configuration.needsChecksum) max.checksum else null,
                    )

                    reportUpToDate -> GradleUpdateInfo(
                        currentVersion = GradleVersion(configuration.version),
                        updatedVersion = GradleVersion(configuration.version),
                        checksum = null,
                    )

                    else -> null
                }
            },
            onFailure = { null }
        )
    }
}
