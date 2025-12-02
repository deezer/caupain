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
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal abstract class AbstractVersionResolver<T : Any>(
    private val httpClient: HttpClient,
    private val ioDispatcher: CoroutineDispatcher
) {
    protected abstract fun T.isUpdatedVersion(version: GradleDependencyVersion.Static): Boolean

    @Suppress("SuspendFunWithCoroutineScopeReceiver") // We need to use HttpClient as receiver
    protected abstract suspend fun HttpClient.getAvailableVersions(item: T): Sequence<GradleDependencyVersion>

    protected abstract suspend fun canSelectVersion(
        item: T,
        version: GradleDependencyVersion.Static
    ): Boolean

    @Suppress("ComplexCondition")
    suspend fun findUpdatedVersion(item: T): GradleDependencyVersion.Static? {
        val versions = withContext(ioDispatcher) {
            httpClient.getAvailableVersions(item)
        }
        var max: GradleDependencyVersion.Static? = null
        for (version in versions) {
            if (
                version is GradleDependencyVersion.Static
                && item.isUpdatedVersion(version)
                && canSelectVersion(item, version)
                && (max == null || version > max)
            ) {
                max = version
            }
        }
        return max
    }
}