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

import com.deezer.caupain.internal.processRequest
import com.deezer.caupain.model.GradleDependencyVersion
import com.deezer.caupain.model.Logger
import com.deezer.caupain.model.github.Release
import com.deezer.caupain.model.github.SearchResults
import com.deezer.caupain.model.maven.MavenInfo
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.URLParserException
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import io.ktor.http.takeFrom
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class GithubReleaseNoteResolver(
    private val httpClient: HttpClient,
    private val ioDispatcher: CoroutineDispatcher,
    private val logger: Logger,
    private val githubToken: String? = null
) {
    suspend fun getReleaseNoteUrl(
        mavenInfo: MavenInfo?,
        updatedVersion: GradleDependencyVersion.Static
    ): String? {
        if (!checkToken()) return null
        val developerUrl = mavenInfo
            ?.scm
            ?.url
            ?.takeUnless { it.isBlank() }
            ?.replace("git@", "https://")
            ?.let { url ->
                try {
                    Url(url)
                } catch (_: URLParserException) {
                    null
                }
            }
            ?: return null
        if (developerUrl.host != GITHUB_HOST) return null
        return getReleaseNoteUrl(
            repositoryUrl = developerUrl,
            version = updatedVersion.exactVersion.toString()
        )
    }

    suspend fun getReleaseNoteUrl(
        repositoryUrl: Url,
        version: String
    ): String? {
        if (!checkToken()) return null
        // Retrieve owner and repository from the URL
        val urlSegments = repositoryUrl.segments
        if (urlSegments.size < 2) {
            logger.warn("Invalid GitHub repository URL: $repositoryUrl")
            return null
        }
        val owner = urlSegments[urlSegments.lastIndex - 1]
        val repository = urlSegments.last()
        // First, let's check if we find the version in the releases
        val releaseNotes = withContext(ioDispatcher) {
            httpClient.processRequest<List<Release>>(
                default = emptyList(),
                onRecoverableError = { error ->
                    logger.error("Failed to fetch releases for $owner/$repository", error)
                }
            ) {
                getGithubResource("repos", owner, repository, "releases")
            }
        }
        val releaseNote = releaseNotes.firstOrNull { it.matches(version) }
        if (releaseNote != null) return releaseNote.url
        // If not present, try to find the changelog file in the repository
        val searchResults = withContext(ioDispatcher) {
            httpClient.processRequest<SearchResults?>(
                default = null,
                onRecoverableError = { error ->
                    logger.error("Failed to search for changelog in $owner/$repository", error)
                }
            ) {
                getGithubResource("search", "code") {
                    parameters["q"] = "$version repo:$owner/$repository filename:CHANGELOG.md"
                }
            }
        }
        if (searchResults != null && searchResults.totalCount > 0) {
            return searchResults.items.firstOrNull()?.url
        }
        return null
    }

    private fun checkToken(): Boolean {
        if (githubToken == null) {
            logger.warn("GitHub token is required to access release notes")
            return false
        }
        return true
    }

    @Suppress("SuspendFunWithCoroutineScopeReceiver") // We need to use HttpClient as receiver
    private suspend fun HttpClient.getGithubResource(
        vararg pathSegments: String,
        urlBuilder: URLBuilder.() -> Unit = {}
    ): HttpResponse {
        return get {
            url {
                takeFrom(BASE_API_URL)
                appendPathSegments(components = pathSegments)
                urlBuilder()
            }
            header(HttpHeaders.Authorization, "Bearer $githubToken")
            header("X-GitHub-Api-Version", "2022-11-28")
            header(HttpHeaders.Accept, "application/vnd.github+json")
        }
    }

    companion object {
        private val BASE_API_URL = Url("https://api.github.com")
        private const val GITHUB_HOST = "github.com"
    }
}