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
import io.github.z4kn4fein.semver.Version
import io.github.z4kn4fein.semver.VersionFormatException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class GradleVersionResolver(
    private val httpClient: HttpClient,
    private val gradleCurrentVersionUrl: String,
    private val ioDispatcher: CoroutineDispatcher
) {
    suspend fun getUpdatedVersion(currentGradleVersion: String): String? {
        return try {
            val currentVersion = Version.parse(currentGradleVersion, strict = false)
            val updatedVersionString = withContext(ioDispatcher) {
                httpClient
                    .get(gradleCurrentVersionUrl)
                    .takeIf { it.status.isSuccess() }
                    ?.body<GradleVersion>()
                    ?.version
            }
            val updatedVersion = updatedVersionString?.let { Version.parse(it, strict = false) }
            return if (updatedVersion == null || updatedVersion <= currentVersion) {
                null
            } else {
                updatedVersionString
            }
        } catch (ignored: VersionFormatException) {
            null
        }
    }
}