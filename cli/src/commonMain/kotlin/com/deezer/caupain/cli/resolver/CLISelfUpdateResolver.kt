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

package com.deezer.caupain.cli.resolver

import com.deezer.caupain.DependencyUpdateChecker
import com.deezer.caupain.cli.BuildConfig
import com.deezer.caupain.cli.serialization.GithubRelease
import com.deezer.caupain.model.GradleDependencyVersion
import com.deezer.caupain.model.Logger
import com.deezer.caupain.model.SelfUpdateInfo
import com.deezer.caupain.model.versionCatalog.VersionCatalog
import com.deezer.caupain.resolver.SelfUpdateResolver
import io.ktor.client.call.body
import io.ktor.client.plugins.SendCountExceedException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMessageBuilder
import io.ktor.http.isSuccess
import io.ktor.serialization.ContentConvertException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import okio.FileSystem

internal class CLISelfUpdateResolver(
    private val logger: Logger,
    private val ioDispatcher: CoroutineDispatcher,
    private val fileSystem: FileSystem,
    private val githubToken: String? = null
) : SelfUpdateResolver {

    override suspend fun resolveSelfUpdate(
        checker: DependencyUpdateChecker,
        versionCatalogs: List<VersionCatalog>
    ): SelfUpdateInfo? {
        val latestTag = withContext(ioDispatcher) {
            try {
                checker
                    .httpClient
                    .get(UPDATE_URL) {
                        header("X-GitHub-Api-Version", "2022-11-28")
                        bearerTokenHeader(githubToken)
                        header(HttpHeaders.Accept, "application/vnd.github+json")
                    }
                    .takeIf { it.status.isSuccess() }
                    ?.body<GithubRelease>()
                    ?.tagName
            } catch (ignored: IOException) {
                logger.error(ERROR_MESSAGE, ignored)
                null
            } catch (ignored: SendCountExceedException) {
                logger.error(ERROR_MESSAGE, ignored)
                null
            } catch (ignored: ContentConvertException) {
                logger.error(ERROR_MESSAGE, ignored)
                null
            }
        } ?: return null
        val updatedVersion = TAG_REGEX
            .find(latestTag)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { GradleDependencyVersion(it) as? GradleDependencyVersion.Static }
            ?: return null
        val currentVersion =
            GradleDependencyVersion(BuildConfig.VERSION) as GradleDependencyVersion.Static
        return if (updatedVersion > currentVersion) {
            SelfUpdateInfo(
                currentVersion = currentVersion.toString(),
                updatedVersion = updatedVersion.toString(),
                sources = listOf(SelfUpdateInfo.Source.GITHUB_RELEASES)
                        + getPossibleUpdateSources(fileSystem)
            )
        } else {
            null
        }
    }

    companion object {
        private val TAG_REGEX = Regex("v(.*)")
        const val UPDATE_URL = "https://api.github.com/repos/deezer/caupain/releases/latest"
        val ERROR_MESSAGE = "Failed to fetch latest release from $UPDATE_URL"
    }
}

private fun HttpMessageBuilder.bearerTokenHeader(token: String?) {
    if (token != null) header(HttpHeaders.Authorization, "Bearer $token")
}

internal expect fun getPossibleUpdateSources(fileSystem: FileSystem): List<SelfUpdateInfo.Source>
