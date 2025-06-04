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

import com.deezer.caupain.BuildKonfig
import com.deezer.caupain.DependencyUpdateChecker
import com.deezer.caupain.cli.serialization.GithubRelease
import com.deezer.caupain.model.GradleDependencyVersion
import com.deezer.caupain.model.SelfUpdateInfo
import com.deezer.caupain.model.versionCatalog.VersionCatalog
import com.deezer.caupain.resolver.SelfUpdateResolver
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okio.FileSystem

internal class CLISelfUpdateResolver(
    private val ioDispatcher: CoroutineDispatcher,
    private val fileSystem: FileSystem,
) : SelfUpdateResolver {

    override suspend fun resolveSelfUpdate(
        checker: DependencyUpdateChecker,
        versionCatalogs: List<VersionCatalog>
    ): SelfUpdateInfo? {
        val latestRelease = withContext(ioDispatcher) {
            checker
                .httpClient
                .get(UPDATE_URL) { header("X-GitHub-Api-Version", "2022-11-28") }
                .takeIf { it.status.isSuccess() }
                ?.body<GithubRelease>()
        } ?: return null
        val updatedVersion = TAG_REGEX
            .find(latestRelease.tagName)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { GradleDependencyVersion(it) as? GradleDependencyVersion.Static }
            ?: return null
        val currentVersion =
            GradleDependencyVersion(BuildKonfig.VERSION) as GradleDependencyVersion.Static
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
    }
}

internal expect fun getPossibleUpdateSources(fileSystem: FileSystem): List<SelfUpdateInfo.Source>