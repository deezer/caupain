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

import com.deezer.caupain.model.GradleDependencyVersion
import com.deezer.caupain.model.Logger
import com.deezer.caupain.model.gradle.GradleStabilityLevel
import com.deezer.caupain.model.gradle.GradleToolVersion
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.io.IOException

internal class GradleVersionResolver(
    httpClient: HttpClient,
    private val logger: Logger,
    private val gradleVersionsUrl: String,
    private val stabilityLevel: GradleStabilityLevel,
    ioDispatcher: CoroutineDispatcher
) {
    private val versionResolver = object : AbstractVersionResolver<GradleDependencyVersion>(
        httpClient = httpClient,
        ioDispatcher = ioDispatcher
    ) {
        override fun GradleDependencyVersion.isUpdatedVersion(version: GradleDependencyVersion.Static): Boolean =
            isUpdate(version)

        override suspend fun HttpClient.getAvailableVersions(item: GradleDependencyVersion): Sequence<GradleDependencyVersion> {
            return try {
                get(gradleVersionsUrl)
                    .takeIf { it.status.isSuccess() }
                    ?.body<List<GradleToolVersion>>()
                    ?.asSequence()
                    ?.filter { it.level <= stabilityLevel }
                    ?.map { it.version }
                    .orEmpty()
            } catch (ignored: IOException) {
                logger.error("Failed to fetch Gradle versions from $gradleVersionsUrl", ignored)
                emptySequence()
            }
        }

        override fun canSelectVersion(
            item: GradleDependencyVersion,
            version: GradleDependencyVersion.Static
        ): Boolean = true
    }


    suspend fun getUpdatedVersion(currentGradleVersion: String): String? = versionResolver
        .findUpdatedVersion(GradleDependencyVersion(currentGradleVersion))
        ?.toString()
}