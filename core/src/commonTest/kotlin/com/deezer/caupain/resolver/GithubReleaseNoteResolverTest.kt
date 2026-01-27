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

import com.deezer.caupain.model.Logger
import com.deezer.caupain.model.maven.MavenInfo
import com.deezer.caupain.model.maven.SCMInfos
import com.deezer.caupain.serialization.DefaultJson
import com.deezer.caupain.toStaticVersion
import dev.mokkery.MockMode
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.jsonIo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalSerializationApi::class)
class GithubReleaseNoteResolverTest {

    private var operationError: Operation? = null

    private lateinit var engine: MockEngine

    private lateinit var logger: Logger

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setup() {
        logger = mock(MockMode.autoUnit)
        engine = MockEngine { requestData ->
            handleRequest(this, requestData)
                ?: respond("Not found", HttpStatusCode.NotFound)
        }
    }

    @AfterTest
    fun teardown() {
        engine.close()
        operationError = null
    }

    private fun createResolver(token: String? = TOKEN): GithubReleaseNoteResolver {
        return GithubReleaseNoteResolver(
            httpClient = HttpClient(engine) {
                install(ContentNegotiation) {
                    jsonIo(DefaultJson)
                }
            },
            ioDispatcher = testDispatcher,
            logger = logger,
            githubToken = token
        )
    }

    private fun handleRequest(
        scope: MockRequestHandleScope,
        requestData: HttpRequestData
    ): HttpResponseData? {
        val url = requestData.url
        // Check authentification
        val authHeader = requestData.headers[HttpHeaders.Authorization]
        if (authHeader != "Bearer $TOKEN") {
            return scope.respond("Unauthorized", HttpStatusCode.Unauthorized)
        }
        return when (url) {
            RELEASE_NOTE_GITHUB_URL -> {
                if (operationError == Operation.RELEASE_NOTES) {
                    throw TestException()
                }
                scope.respond(
                    content = RELEASE_NOTES,
                    headers = headersOf(HttpHeaders.ContentType, "application/vnd.github+json")
                )
            }

            REPO_GITHUB_URL -> {
                if (operationError == Operation.CHANGELOG) {
                    throw TestException()
                }
                scope.respond(
                    content = REPO,
                    headers = headersOf(HttpHeaders.ContentType, "application/vnd.github+json")
                )
            }

            TREES_GITHUB_URL -> {
                if (operationError == Operation.CHANGELOG) {
                    throw TestException()
                }
                scope.respond(
                    content = TREES,
                    headers = headersOf(HttpHeaders.ContentType, "application/vnd.github+json")
                )
            }

            else -> {
                null
            }
        }
    }

    @Test
    fun testNoToken() = runTest(testDispatcher) {
        val resolver = createResolver(null)
        assertNull(resolver.getReleaseNoteUrl(MAVEN_INFO_RELEASE, "1.0.0".toStaticVersion()))
    }

    @Test
    fun testNotGithub() = runTest(testDispatcher) {
        val resolver = createResolver()
        assertNull(resolver.getReleaseNoteUrl(MAVEN_INFO_NO_GITHUB, "1.0.0".toStaticVersion()))
    }

    @Test
    fun testReleaseNote() = runTest(testDispatcher) {
        assertEquals(
            expected = RELEASE_NOTES_URL,
            actual = createResolver().getReleaseNoteUrl(
                MAVEN_INFO_RELEASE,
                "1.5.1".toStaticVersion()
            )
        )
    }

    @Test
    fun testReleaseNoteError() = runTest(testDispatcher) {
        operationError = Operation.RELEASE_NOTES
        assertNull(
            createResolver().getReleaseNoteUrl(
                MAVEN_INFO_RELEASE,
                "1.5.1".toStaticVersion()
            )
        )
        verify {
            logger.error(
                message = any(),
                throwable = any<TestException>()
            )
        }
    }

    @Test
    fun testChangelog() = runTest(testDispatcher) {
        assertEquals(
            expected = CHANGELOG_URL,
            actual = createResolver().getReleaseNoteUrl(
                MAVEN_INFO_CHANGELOG,
                "2.0.0".toStaticVersion()
            )
        )
    }

    @Test
    fun testChangelogError() = runTest(testDispatcher) {
        operationError = Operation.CHANGELOG
        assertNull(
            createResolver().getReleaseNoteUrl(
                MAVEN_INFO_CHANGELOG,
                "2.0.0".toStaticVersion()
            )
        )
        verify {
            logger.error(
                message = any(),
                throwable = any<TestException>()
            )
        }
    }

    private enum class Operation {
        RELEASE_NOTES,
        CHANGELOG,
    }
}

private const val TOKEN = "TEST"

private const val OWNER = "owner"
private const val RELEASE_REPO = "repo-release"
private const val CHANGELOG_REPO = "repo-changelog"

private const val RELEASE_NOTES_URL = "http://www.example.com/release-note"
private const val CHANGELOG_URL = "https://github.com/$OWNER/$CHANGELOG_REPO/blob/main/CHANGELOG.md"

private val RELEASE_NOTE_GITHUB_URL =
    Url("https://api.github.com/repos/$OWNER/$RELEASE_REPO/releases")

private const val RELEASE_NOTES = """[
          {
            "url": "https://api.github.com/repos/deezer/caupain/releases/238972916",
            "assets_url": "https://api.github.com/repos/deezer/caupain/releases/238972916/assets",
            "upload_url": "https://uploads.github.com/repos/deezer/caupain/releases/238972916/assets{?name,label}",
            "html_url": "$RELEASE_NOTES_URL",
            "id": 238972916,
            "author": {
              "login": "github-actions[bot]",
              "id": 41898282,
              "node_id": "MDM6Qm90NDE4OTgyODI=",
              "avatar_url": "https://avatars.githubusercontent.com/in/15368?v=4",
              "gravatar_id": "",
              "url": "https://api.github.com/users/github-actions%5Bbot%5D",
              "html_url": "https://github.com/apps/github-actions",
              "followers_url": "https://api.github.com/users/github-actions%5Bbot%5D/followers",
              "following_url": "https://api.github.com/users/github-actions%5Bbot%5D/following{/other_user}",
              "gists_url": "https://api.github.com/users/github-actions%5Bbot%5D/gists{/gist_id}",
              "starred_url": "https://api.github.com/users/github-actions%5Bbot%5D/starred{/owner}{/repo}",
              "subscriptions_url": "https://api.github.com/users/github-actions%5Bbot%5D/subscriptions",
              "organizations_url": "https://api.github.com/users/github-actions%5Bbot%5D/orgs",
              "repos_url": "https://api.github.com/users/github-actions%5Bbot%5D/repos",
              "events_url": "https://api.github.com/users/github-actions%5Bbot%5D/events{/privacy}",
              "received_events_url": "https://api.github.com/users/github-actions%5Bbot%5D/received_events",
              "type": "Bot",
              "user_view_type": "public",
              "site_admin": false
            },
            "node_id": "RE_kwDOOYhowc4OPm_0",
            "tag_name": "v1.5.1",
            "target_commitish": "main",
            "name": "v1.5.1",
            "draft": false,
            "immutable": false,
            "prerelease": false,
            "created_at": "2025-08-11T10:29:28Z",
            "updated_at": "2025-08-11T10:44:29Z",
            "published_at": "2025-08-11T10:44:28Z",
            "assets": [
              {
                "url": "https://api.github.com/repos/deezer/caupain/releases/assets/281398301",
                "id": 281398301,
                "node_id": "RA_kwDOOYhowc4Qxcwd",
                "name": "caupain-1.5.1-jvm.zip",
                "label": "",
                "uploader": {
                  "login": "github-actions[bot]",
                  "id": 41898282,
                  "node_id": "MDM6Qm90NDE4OTgyODI=",
                  "avatar_url": "https://avatars.githubusercontent.com/in/15368?v=4",
                  "gravatar_id": "",
                  "url": "https://api.github.com/users/github-actions%5Bbot%5D",
                  "html_url": "https://github.com/apps/github-actions",
                  "followers_url": "https://api.github.com/users/github-actions%5Bbot%5D/followers",
                  "following_url": "https://api.github.com/users/github-actions%5Bbot%5D/following{/other_user}",
                  "gists_url": "https://api.github.com/users/github-actions%5Bbot%5D/gists{/gist_id}",
                  "starred_url": "https://api.github.com/users/github-actions%5Bbot%5D/starred{/owner}{/repo}",
                  "subscriptions_url": "https://api.github.com/users/github-actions%5Bbot%5D/subscriptions",
                  "organizations_url": "https://api.github.com/users/github-actions%5Bbot%5D/orgs",
                  "repos_url": "https://api.github.com/users/github-actions%5Bbot%5D/repos",
                  "events_url": "https://api.github.com/users/github-actions%5Bbot%5D/events{/privacy}",
                  "received_events_url": "https://api.github.com/users/github-actions%5Bbot%5D/received_events",
                  "type": "Bot",
                  "user_view_type": "public",
                  "site_admin": false
                },
                "content_type": "application/zip",
                "state": "uploaded",
                "size": 16334474,
                "digest": "sha256:d9ea218af4abe5dc212b4c0e8c6be23c1ddee24752c502b329de00a9849faab8",
                "download_count": 0,
                "created_at": "2025-08-11T10:44:28Z",
                "updated_at": "2025-08-11T10:44:29Z",
                "browser_download_url": "https://github.com/deezer/caupain/releases/download/v1.5.1/caupain-1.5.1-jvm.zip"
              },
              {
                "url": "https://api.github.com/repos/deezer/caupain/releases/assets/281398306",
                "id": 281398306,
                "node_id": "RA_kwDOOYhowc4Qxcwi",
                "name": "caupain-1.5.1-linux-arm.zip",
                "label": "",
                "uploader": {
                  "login": "github-actions[bot]",
                  "id": 41898282,
                  "node_id": "MDM6Qm90NDE4OTgyODI=",
                  "avatar_url": "https://avatars.githubusercontent.com/in/15368?v=4",
                  "gravatar_id": "",
                  "url": "https://api.github.com/users/github-actions%5Bbot%5D",
                  "html_url": "https://github.com/apps/github-actions",
                  "followers_url": "https://api.github.com/users/github-actions%5Bbot%5D/followers",
                  "following_url": "https://api.github.com/users/github-actions%5Bbot%5D/following{/other_user}",
                  "gists_url": "https://api.github.com/users/github-actions%5Bbot%5D/gists{/gist_id}",
                  "starred_url": "https://api.github.com/users/github-actions%5Bbot%5D/starred{/owner}{/repo}",
                  "subscriptions_url": "https://api.github.com/users/github-actions%5Bbot%5D/subscriptions",
                  "organizations_url": "https://api.github.com/users/github-actions%5Bbot%5D/orgs",
                  "repos_url": "https://api.github.com/users/github-actions%5Bbot%5D/repos",
                  "events_url": "https://api.github.com/users/github-actions%5Bbot%5D/events{/privacy}",
                  "received_events_url": "https://api.github.com/users/github-actions%5Bbot%5D/received_events",
                  "type": "Bot",
                  "user_view_type": "public",
                  "site_admin": false
                },
                "content_type": "application/zip",
                "state": "uploaded",
                "size": 6330779,
                "digest": "sha256:7154f9020b6c7b48ea70c97f220e81d7807144c295d4ee68ab89e9f977885c9e",
                "download_count": 0,
                "created_at": "2025-08-11T10:44:28Z",
                "updated_at": "2025-08-11T10:44:29Z",
                "browser_download_url": "https://github.com/deezer/caupain/releases/download/v1.5.1/caupain-1.5.1-linux-arm.zip"
              },
              {
                "url": "https://api.github.com/repos/deezer/caupain/releases/assets/281398304",
                "id": 281398304,
                "node_id": "RA_kwDOOYhowc4Qxcwg",
                "name": "caupain-1.5.1-linux.zip",
                "label": "",
                "uploader": {
                  "login": "github-actions[bot]",
                  "id": 41898282,
                  "node_id": "MDM6Qm90NDE4OTgyODI=",
                  "avatar_url": "https://avatars.githubusercontent.com/in/15368?v=4",
                  "gravatar_id": "",
                  "url": "https://api.github.com/users/github-actions%5Bbot%5D",
                  "html_url": "https://github.com/apps/github-actions",
                  "followers_url": "https://api.github.com/users/github-actions%5Bbot%5D/followers",
                  "following_url": "https://api.github.com/users/github-actions%5Bbot%5D/following{/other_user}",
                  "gists_url": "https://api.github.com/users/github-actions%5Bbot%5D/gists{/gist_id}",
                  "starred_url": "https://api.github.com/users/github-actions%5Bbot%5D/starred{/owner}{/repo}",
                  "subscriptions_url": "https://api.github.com/users/github-actions%5Bbot%5D/subscriptions",
                  "organizations_url": "https://api.github.com/users/github-actions%5Bbot%5D/orgs",
                  "repos_url": "https://api.github.com/users/github-actions%5Bbot%5D/repos",
                  "events_url": "https://api.github.com/users/github-actions%5Bbot%5D/events{/privacy}",
                  "received_events_url": "https://api.github.com/users/github-actions%5Bbot%5D/received_events",
                  "type": "Bot",
                  "user_view_type": "public",
                  "site_admin": false
                },
                "content_type": "application/zip",
                "state": "uploaded",
                "size": 6243846,
                "digest": "sha256:1d6c5f875d6283a074dd05c134ae805c51caab03b4cd0709b6ae420dbb223c57",
                "download_count": 0,
                "created_at": "2025-08-11T10:44:28Z",
                "updated_at": "2025-08-11T10:44:29Z",
                "browser_download_url": "https://github.com/deezer/caupain/releases/download/v1.5.1/caupain-1.5.1-linux.zip"
              },
              {
                "url": "https://api.github.com/repos/deezer/caupain/releases/assets/281398307",
                "id": 281398307,
                "node_id": "RA_kwDOOYhowc4Qxcwj",
                "name": "caupain-1.5.1-macos-intel.zip",
                "label": "",
                "uploader": {
                  "login": "github-actions[bot]",
                  "id": 41898282,
                  "node_id": "MDM6Qm90NDE4OTgyODI=",
                  "avatar_url": "https://avatars.githubusercontent.com/in/15368?v=4",
                  "gravatar_id": "",
                  "url": "https://api.github.com/users/github-actions%5Bbot%5D",
                  "html_url": "https://github.com/apps/github-actions",
                  "followers_url": "https://api.github.com/users/github-actions%5Bbot%5D/followers",
                  "following_url": "https://api.github.com/users/github-actions%5Bbot%5D/following{/other_user}",
                  "gists_url": "https://api.github.com/users/github-actions%5Bbot%5D/gists{/gist_id}",
                  "starred_url": "https://api.github.com/users/github-actions%5Bbot%5D/starred{/owner}{/repo}",
                  "subscriptions_url": "https://api.github.com/users/github-actions%5Bbot%5D/subscriptions",
                  "organizations_url": "https://api.github.com/users/github-actions%5Bbot%5D/orgs",
                  "repos_url": "https://api.github.com/users/github-actions%5Bbot%5D/repos",
                  "events_url": "https://api.github.com/users/github-actions%5Bbot%5D/events{/privacy}",
                  "received_events_url": "https://api.github.com/users/github-actions%5Bbot%5D/received_events",
                  "type": "Bot",
                  "user_view_type": "public",
                  "site_admin": false
                },
                "content_type": "application/zip",
                "state": "uploaded",
                "size": 3227171,
                "digest": "sha256:a85174c15bb423a9a41ccaff314da8442f710599e6c91d8bc4bc5f85051e764b",
                "download_count": 0,
                "created_at": "2025-08-11T10:44:28Z",
                "updated_at": "2025-08-11T10:44:28Z",
                "browser_download_url": "https://github.com/deezer/caupain/releases/download/v1.5.1/caupain-1.5.1-macos-intel.zip"
              },
              {
                "url": "https://api.github.com/repos/deezer/caupain/releases/assets/281398302",
                "id": 281398302,
                "node_id": "RA_kwDOOYhowc4Qxcwe",
                "name": "caupain-1.5.1-macos-silicon.zip",
                "label": "",
                "uploader": {
                  "login": "github-actions[bot]",
                  "id": 41898282,
                  "node_id": "MDM6Qm90NDE4OTgyODI=",
                  "avatar_url": "https://avatars.githubusercontent.com/in/15368?v=4",
                  "gravatar_id": "",
                  "url": "https://api.github.com/users/github-actions%5Bbot%5D",
                  "html_url": "https://github.com/apps/github-actions",
                  "followers_url": "https://api.github.com/users/github-actions%5Bbot%5D/followers",
                  "following_url": "https://api.github.com/users/github-actions%5Bbot%5D/following{/other_user}",
                  "gists_url": "https://api.github.com/users/github-actions%5Bbot%5D/gists{/gist_id}",
                  "starred_url": "https://api.github.com/users/github-actions%5Bbot%5D/starred{/owner}{/repo}",
                  "subscriptions_url": "https://api.github.com/users/github-actions%5Bbot%5D/subscriptions",
                  "organizations_url": "https://api.github.com/users/github-actions%5Bbot%5D/orgs",
                  "repos_url": "https://api.github.com/users/github-actions%5Bbot%5D/repos",
                  "events_url": "https://api.github.com/users/github-actions%5Bbot%5D/events{/privacy}",
                  "received_events_url": "https://api.github.com/users/github-actions%5Bbot%5D/received_events",
                  "type": "Bot",
                  "user_view_type": "public",
                  "site_admin": false
                },
                "content_type": "application/zip",
                "state": "uploaded",
                "size": 3123980,
                "digest": "sha256:9ab7f0d27d14b3dc92c1696ce9956e434f7f5b9990af1375ffe05f3d042026b1",
                "download_count": 1,
                "created_at": "2025-08-11T10:44:28Z",
                "updated_at": "2025-08-11T10:44:29Z",
                "browser_download_url": "https://github.com/deezer/caupain/releases/download/v1.5.1/caupain-1.5.1-macos-silicon.zip"
              },
              {
                "url": "https://api.github.com/repos/deezer/caupain/releases/assets/281398305",
                "id": 281398305,
                "node_id": "RA_kwDOOYhowc4Qxcwh",
                "name": "caupain-1.5.1-windows.zip",
                "label": "",
                "uploader": {
                  "login": "github-actions[bot]",
                  "id": 41898282,
                  "node_id": "MDM6Qm90NDE4OTgyODI=",
                  "avatar_url": "https://avatars.githubusercontent.com/in/15368?v=4",
                  "gravatar_id": "",
                  "url": "https://api.github.com/users/github-actions%5Bbot%5D",
                  "html_url": "https://github.com/apps/github-actions",
                  "followers_url": "https://api.github.com/users/github-actions%5Bbot%5D/followers",
                  "following_url": "https://api.github.com/users/github-actions%5Bbot%5D/following{/other_user}",
                  "gists_url": "https://api.github.com/users/github-actions%5Bbot%5D/gists{/gist_id}",
                  "starred_url": "https://api.github.com/users/github-actions%5Bbot%5D/starred{/owner}{/repo}",
                  "subscriptions_url": "https://api.github.com/users/github-actions%5Bbot%5D/subscriptions",
                  "organizations_url": "https://api.github.com/users/github-actions%5Bbot%5D/orgs",
                  "repos_url": "https://api.github.com/users/github-actions%5Bbot%5D/repos",
                  "events_url": "https://api.github.com/users/github-actions%5Bbot%5D/events{/privacy}",
                  "received_events_url": "https://api.github.com/users/github-actions%5Bbot%5D/received_events",
                  "type": "Bot",
                  "user_view_type": "public",
                  "site_admin": false
                },
                "content_type": "application/zip",
                "state": "uploaded",
                "size": 4026848,
                "digest": "sha256:f89a1c81fd60461f616af03cd47f95da1de9b9843c4071013fcca13667fff98f",
                "download_count": 0,
                "created_at": "2025-08-11T10:44:28Z",
                "updated_at": "2025-08-11T10:44:29Z",
                "browser_download_url": "https://github.com/deezer/caupain/releases/download/v1.5.1/caupain-1.5.1-windows.zip"
              },
              {
                "url": "https://api.github.com/repos/deezer/caupain/releases/assets/281398308",
                "id": 281398308,
                "node_id": "RA_kwDOOYhowc4Qxcwk",
                "name": "caupain_1.5.1-1_amd64.deb",
                "label": "",
                "uploader": {
                  "login": "github-actions[bot]",
                  "id": 41898282,
                  "node_id": "MDM6Qm90NDE4OTgyODI=",
                  "avatar_url": "https://avatars.githubusercontent.com/in/15368?v=4",
                  "gravatar_id": "",
                  "url": "https://api.github.com/users/github-actions%5Bbot%5D",
                  "html_url": "https://github.com/apps/github-actions",
                  "followers_url": "https://api.github.com/users/github-actions%5Bbot%5D/followers",
                  "following_url": "https://api.github.com/users/github-actions%5Bbot%5D/following{/other_user}",
                  "gists_url": "https://api.github.com/users/github-actions%5Bbot%5D/gists{/gist_id}",
                  "starred_url": "https://api.github.com/users/github-actions%5Bbot%5D/starred{/owner}{/repo}",
                  "subscriptions_url": "https://api.github.com/users/github-actions%5Bbot%5D/subscriptions",
                  "organizations_url": "https://api.github.com/users/github-actions%5Bbot%5D/orgs",
                  "repos_url": "https://api.github.com/users/github-actions%5Bbot%5D/repos",
                  "events_url": "https://api.github.com/users/github-actions%5Bbot%5D/events{/privacy}",
                  "received_events_url": "https://api.github.com/users/github-actions%5Bbot%5D/received_events",
                  "type": "Bot",
                  "user_view_type": "public",
                  "site_admin": false
                },
                "content_type": "application/x-debian-package",
                "state": "uploaded",
                "size": 6246240,
                "digest": "sha256:c1e11364e31775ce81af0b3d8d22f3b0d2c601e471091f244c3c62ea5c7917bf",
                "download_count": 1,
                "created_at": "2025-08-11T10:44:28Z",
                "updated_at": "2025-08-11T10:44:29Z",
                "browser_download_url": "https://github.com/deezer/caupain/releases/download/v1.5.1/caupain_1.5.1-1_amd64.deb"
              },
              {
                "url": "https://api.github.com/repos/deezer/caupain/releases/assets/281398303",
                "id": 281398303,
                "node_id": "RA_kwDOOYhowc4Qxcwf",
                "name": "caupain_1.5.1-1_arm64.deb",
                "label": "",
                "uploader": {
                  "login": "github-actions[bot]",
                  "id": 41898282,
                  "node_id": "MDM6Qm90NDE4OTgyODI=",
                  "avatar_url": "https://avatars.githubusercontent.com/in/15368?v=4",
                  "gravatar_id": "",
                  "url": "https://api.github.com/users/github-actions%5Bbot%5D",
                  "html_url": "https://github.com/apps/github-actions",
                  "followers_url": "https://api.github.com/users/github-actions%5Bbot%5D/followers",
                  "following_url": "https://api.github.com/users/github-actions%5Bbot%5D/following{/other_user}",
                  "gists_url": "https://api.github.com/users/github-actions%5Bbot%5D/gists{/gist_id}",
                  "starred_url": "https://api.github.com/users/github-actions%5Bbot%5D/starred{/owner}{/repo}",
                  "subscriptions_url": "https://api.github.com/users/github-actions%5Bbot%5D/subscriptions",
                  "organizations_url": "https://api.github.com/users/github-actions%5Bbot%5D/orgs",
                  "repos_url": "https://api.github.com/users/github-actions%5Bbot%5D/repos",
                  "events_url": "https://api.github.com/users/github-actions%5Bbot%5D/events{/privacy}",
                  "received_events_url": "https://api.github.com/users/github-actions%5Bbot%5D/received_events",
                  "type": "Bot",
                  "user_view_type": "public",
                  "site_admin": false
                },
                "content_type": "application/x-debian-package",
                "state": "uploaded",
                "size": 6333060,
                "digest": "sha256:5f04115ced198d1cd1b5a8d3ca7185224e1b4780da89ce3f841a4acda2c4811f",
                "download_count": 1,
                "created_at": "2025-08-11T10:44:28Z",
                "updated_at": "2025-08-11T10:44:29Z",
                "browser_download_url": "https://github.com/deezer/caupain/releases/download/v1.5.1/caupain_1.5.1-1_arm64.deb"
              }
            ],
            "tarball_url": "https://api.github.com/repos/deezer/caupain/tarball/v1.5.1",
            "zipball_url": "https://api.github.com/repos/deezer/caupain/zipball/v1.5.1",
            "body": "### Fixed\n\n- Fix version replacer issue when temporary file system is on another volumes (#51)\n",
            "reactions": {
              "url": "https://api.github.com/repos/deezer/caupain/releases/238972916/reactions",
              "total_count": 2,
              "+1": 0,
              "-1": 0,
              "laugh": 0,
              "hooray": 2,
              "confused": 0,
              "heart": 0,
              "rocket": 0,
              "eyes": 0
            }
          },
          {
            "url": "https://api.github.com/repos/deezer/caupain/releases/238858162",
            "assets_url": "https://api.github.com/repos/deezer/caupain/releases/238858162/assets",
            "upload_url": "https://uploads.github.com/repos/deezer/caupain/releases/238858162/assets{?name,label}",
            "html_url": "https://github.com/deezer/caupain/releases/tag/v1.5.0",
            "id": 238858162,
            "author": {
              "login": "github-actions[bot]",
              "id": 41898282,
              "node_id": "MDM6Qm90NDE4OTgyODI=",
              "avatar_url": "https://avatars.githubusercontent.com/in/15368?v=4",
              "gravatar_id": "",
              "url": "https://api.github.com/users/github-actions%5Bbot%5D",
              "html_url": "https://github.com/apps/github-actions",
              "followers_url": "https://api.github.com/users/github-actions%5Bbot%5D/followers",
              "following_url": "https://api.github.com/users/github-actions%5Bbot%5D/following{/other_user}",
              "gists_url": "https://api.github.com/users/github-actions%5Bbot%5D/gists{/gist_id}",
              "starred_url": "https://api.github.com/users/github-actions%5Bbot%5D/starred{/owner}{/repo}",
              "subscriptions_url": "https://api.github.com/users/github-actions%5Bbot%5D/subscriptions",
              "organizations_url": "https://api.github.com/users/github-actions%5Bbot%5D/orgs",
              "repos_url": "https://api.github.com/users/github-actions%5Bbot%5D/repos",
              "events_url": "https://api.github.com/users/github-actions%5Bbot%5D/events{/privacy}",
              "received_events_url": "https://api.github.com/users/github-actions%5Bbot%5D/received_events",
              "type": "Bot",
              "user_view_type": "public",
              "site_admin": false
            },
            "node_id": "RE_kwDOOYhowc4OPK-y",
            "tag_name": "v1.5.0",
            "target_commitish": "main",
            "name": "v1.5.0",
            "draft": false,
            "immutable": false,
            "prerelease": false,
            "created_at": "2025-08-10T16:12:59Z",
            "updated_at": "2025-08-10T16:33:01Z",
            "published_at": "2025-08-10T16:32:55Z",
            "assets": [
              {
                "url": "https://api.github.com/repos/deezer/caupain/releases/assets/281172763",
                "id": 281172763,
                "node_id": "RA_kwDOOYhowc4Qwlsb",
                "name": "caupain-1.5.0-jvm.zip",
                "label": "",
                "uploader": {
                  "login": "github-actions[bot]",
                  "id": 41898282,
                  "node_id": "MDM6Qm90NDE4OTgyODI=",
                  "avatar_url": "https://avatars.githubusercontent.com/in/15368?v=4",
                  "gravatar_id": "",
                  "url": "https://api.github.com/users/github-actions%5Bbot%5D",
                  "html_url": "https://github.com/apps/github-actions",
                  "followers_url": "https://api.github.com/users/github-actions%5Bbot%5D/followers",
                  "following_url": "https://api.github.com/users/github-actions%5Bbot%5D/following{/other_user}",
                  "gists_url": "https://api.github.com/users/github-actions%5Bbot%5D/gists{/gist_id}",
                  "starred_url": "https://api.github.com/users/github-actions%5Bbot%5D/starred{/owner}{/repo}",
                  "subscriptions_url": "https://api.github.com/users/github-actions%5Bbot%5D/subscriptions",
                  "organizations_url": "https://api.github.com/users/github-actions%5Bbot%5D/orgs",
                  "repos_url": "https://api.github.com/users/github-actions%5Bbot%5D/repos",
                  "events_url": "https://api.github.com/users/github-actions%5Bbot%5D/events{/privacy}",
                  "received_events_url": "https://api.github.com/users/github-actions%5Bbot%5D/received_events",
                  "type": "Bot",
                  "user_view_type": "public",
                  "site_admin": false
                },
                "content_type": "application/zip",
                "state": "uploaded",
                "size": 16334018,
                "digest": "sha256:ac8471cc420ba9abae69f80443f48eab30cb6f7f62c25c5cd27d74423da6728b",
                "download_count": 0,
                "created_at": "2025-08-10T16:33:01Z",
                "updated_at": "2025-08-10T16:33:01Z",
                "browser_download_url": "https://github.com/deezer/caupain/releases/download/v1.5.0/caupain-1.5.0-jvm.zip"
              },
              {
                "url": "https://api.github.com/repos/deezer/caupain/releases/assets/281172769",
                "id": 281172769,
                "node_id": "RA_kwDOOYhowc4Qwlsh",
                "name": "caupain-1.5.0-linux-arm.zip",
                "label": "",
                "uploader": {
                  "login": "github-actions[bot]",
                  "id": 41898282,
                  "node_id": "MDM6Qm90NDE4OTgyODI=",
                  "avatar_url": "https://avatars.githubusercontent.com/in/15368?v=4",
                  "gravatar_id": "",
                  "url": "https://api.github.com/users/github-actions%5Bbot%5D",
                  "html_url": "https://github.com/apps/github-actions",
                  "followers_url": "https://api.github.com/users/github-actions%5Bbot%5D/followers",
                  "following_url": "https://api.github.com/users/github-actions%5Bbot%5D/following{/other_user}",
                  "gists_url": "https://api.github.com/users/github-actions%5Bbot%5D/gists{/gist_id}",
                  "starred_url": "https://api.github.com/users/github-actions%5Bbot%5D/starred{/owner}{/repo}",
                  "subscriptions_url": "https://api.github.com/users/github-actions%5Bbot%5D/subscriptions",
                  "organizations_url": "https://api.github.com/users/github-actions%5Bbot%5D/orgs",
                  "repos_url": "https://api.github.com/users/github-actions%5Bbot%5D/repos",
                  "events_url": "https://api.github.com/users/github-actions%5Bbot%5D/events{/privacy}",
                  "received_events_url": "https://api.github.com/users/github-actions%5Bbot%5D/received_events",
                  "type": "Bot",
                  "user_view_type": "public",
                  "site_admin": false
                },
                "content_type": "application/zip",
                "state": "uploaded",
                "size": 6330247,
                "digest": "sha256:4bc03751e8aab03b891fe2686a8b395c8af0016bf2182374bad3ad220d5a63a6",
                "download_count": 0,
                "created_at": "2025-08-10T16:33:01Z",
                "updated_at": "2025-08-10T16:33:01Z",
                "browser_download_url": "https://github.com/deezer/caupain/releases/download/v1.5.0/caupain-1.5.0-linux-arm.zip"
              },
              {
                "url": "https://api.github.com/repos/deezer/caupain/releases/assets/281172767",
                "id": 281172767,
                "node_id": "RA_kwDOOYhowc4Qwlsf",
                "name": "caupain-1.5.0-linux.zip",
                "label": "",
                "uploader": {
                  "login": "github-actions[bot]",
                  "id": 41898282,
                  "node_id": "MDM6Qm90NDE4OTgyODI=",
                  "avatar_url": "https://avatars.githubusercontent.com/in/15368?v=4",
                  "gravatar_id": "",
                  "url": "https://api.github.com/users/github-actions%5Bbot%5D",
                  "html_url": "https://github.com/apps/github-actions",
                  "followers_url": "https://api.github.com/users/github-actions%5Bbot%5D/followers",
                  "following_url": "https://api.github.com/users/github-actions%5Bbot%5D/following{/other_user}",
                  "gists_url": "https://api.github.com/users/github-actions%5Bbot%5D/gists{/gist_id}",
                  "starred_url": "https://api.github.com/users/github-actions%5Bbot%5D/starred{/owner}{/repo}",
                  "subscriptions_url": "https://api.github.com/users/github-actions%5Bbot%5D/subscriptions",
                  "organizations_url": "https://api.github.com/users/github-actions%5Bbot%5D/orgs",
                  "repos_url": "https://api.github.com/users/github-actions%5Bbot%5D/repos",
                  "events_url": "https://api.github.com/users/github-actions%5Bbot%5D/events{/privacy}",
                  "received_events_url": "https://api.github.com/users/github-actions%5Bbot%5D/received_events",
                  "type": "Bot",
                  "user_view_type": "public",
                  "site_admin": false
                },
                "content_type": "application/zip",
                "state": "uploaded",
                "size": 6243423,
                "digest": "sha256:5f20a27612f426f417d9ba24afa8ece8453ccec3c16b3567ca68578417810be8",
                "download_count": 0,
                "created_at": "2025-08-10T16:33:01Z",
                "updated_at": "2025-08-10T16:33:01Z",
                "browser_download_url": "https://github.com/deezer/caupain/releases/download/v1.5.0/caupain-1.5.0-linux.zip"
              },
              {
                "url": "https://api.github.com/repos/deezer/caupain/releases/assets/281172765",
                "id": 281172765,
                "node_id": "RA_kwDOOYhowc4Qwlsd",
                "name": "caupain-1.5.0-macos-intel.zip",
                "label": "",
                "uploader": {
                  "login": "github-actions[bot]",
                  "id": 41898282,
                  "node_id": "MDM6Qm90NDE4OTgyODI=",
                  "avatar_url": "https://avatars.githubusercontent.com/in/15368?v=4",
                  "gravatar_id": "",
                  "url": "https://api.github.com/users/github-actions%5Bbot%5D",
                  "html_url": "https://github.com/apps/github-actions",
                  "followers_url": "https://api.github.com/users/github-actions%5Bbot%5D/followers",
                  "following_url": "https://api.github.com/users/github-actions%5Bbot%5D/following{/other_user}",
                  "gists_url": "https://api.github.com/users/github-actions%5Bbot%5D/gists{/gist_id}",
                  "starred_url": "https://api.github.com/users/github-actions%5Bbot%5D/starred{/owner}{/repo}",
                  "subscriptions_url": "https://api.github.com/users/github-actions%5Bbot%5D/subscriptions",
                  "organizations_url": "https://api.github.com/users/github-actions%5Bbot%5D/orgs",
                  "repos_url": "https://api.github.com/users/github-actions%5Bbot%5D/repos",
                  "events_url": "https://api.github.com/users/github-actions%5Bbot%5D/events{/privacy}",
                  "received_events_url": "https://api.github.com/users/github-actions%5Bbot%5D/received_events",
                  "type": "Bot",
                  "user_view_type": "public",
                  "site_admin": false
                },
                "content_type": "application/zip",
                "state": "uploaded",
                "size": 3226701,
                "digest": "sha256:4ee330de845ed48356b7bdc335ae27e7be85f78f6d6a3d2498b882df1f2b60b0",
                "download_count": 0,
                "created_at": "2025-08-10T16:33:01Z",
                "updated_at": "2025-08-10T16:33:01Z",
                "browser_download_url": "https://github.com/deezer/caupain/releases/download/v1.5.0/caupain-1.5.0-macos-intel.zip"
              },
              {
                "url": "https://api.github.com/repos/deezer/caupain/releases/assets/281172766",
                "id": 281172766,
                "node_id": "RA_kwDOOYhowc4Qwlse",
                "name": "caupain-1.5.0-macos-silicon.zip",
                "label": "",
                "uploader": {
                  "login": "github-actions[bot]",
                  "id": 41898282,
                  "node_id": "MDM6Qm90NDE4OTgyODI=",
                  "avatar_url": "https://avatars.githubusercontent.com/in/15368?v=4",
                  "gravatar_id": "",
                  "url": "https://api.github.com/users/github-actions%5Bbot%5D",
                  "html_url": "https://github.com/apps/github-actions",
                  "followers_url": "https://api.github.com/users/github-actions%5Bbot%5D/followers",
                  "following_url": "https://api.github.com/users/github-actions%5Bbot%5D/following{/other_user}",
                  "gists_url": "https://api.github.com/users/github-actions%5Bbot%5D/gists{/gist_id}",
                  "starred_url": "https://api.github.com/users/github-actions%5Bbot%5D/starred{/owner}{/repo}",
                  "subscriptions_url": "https://api.github.com/users/github-actions%5Bbot%5D/subscriptions",
                  "organizations_url": "https://api.github.com/users/github-actions%5Bbot%5D/orgs",
                  "repos_url": "https://api.github.com/users/github-actions%5Bbot%5D/repos",
                  "events_url": "https://api.github.com/users/github-actions%5Bbot%5D/events{/privacy}",
                  "received_events_url": "https://api.github.com/users/github-actions%5Bbot%5D/received_events",
                  "type": "Bot",
                  "user_view_type": "public",
                  "site_admin": false
                },
                "content_type": "application/zip",
                "state": "uploaded",
                "size": 3123827,
                "digest": "sha256:b8794640dff388079895b4042b5cb81df99bec75150a29cf35ae8b0dff624e3f",
                "download_count": 0,
                "created_at": "2025-08-10T16:33:01Z",
                "updated_at": "2025-08-10T16:33:01Z",
                "browser_download_url": "https://github.com/deezer/caupain/releases/download/v1.5.0/caupain-1.5.0-macos-silicon.zip"
              },
              {
                "url": "https://api.github.com/repos/deezer/caupain/releases/assets/281172764",
                "id": 281172764,
                "node_id": "RA_kwDOOYhowc4Qwlsc",
                "name": "caupain-1.5.0-windows.zip",
                "label": "",
                "uploader": {
                  "login": "github-actions[bot]",
                  "id": 41898282,
                  "node_id": "MDM6Qm90NDE4OTgyODI=",
                  "avatar_url": "https://avatars.githubusercontent.com/in/15368?v=4",
                  "gravatar_id": "",
                  "url": "https://api.github.com/users/github-actions%5Bbot%5D",
                  "html_url": "https://github.com/apps/github-actions",
                  "followers_url": "https://api.github.com/users/github-actions%5Bbot%5D/followers",
                  "following_url": "https://api.github.com/users/github-actions%5Bbot%5D/following{/other_user}",
                  "gists_url": "https://api.github.com/users/github-actions%5Bbot%5D/gists{/gist_id}",
                  "starred_url": "https://api.github.com/users/github-actions%5Bbot%5D/starred{/owner}{/repo}",
                  "subscriptions_url": "https://api.github.com/users/github-actions%5Bbot%5D/subscriptions",
                  "organizations_url": "https://api.github.com/users/github-actions%5Bbot%5D/orgs",
                  "repos_url": "https://api.github.com/users/github-actions%5Bbot%5D/repos",
                  "events_url": "https://api.github.com/users/github-actions%5Bbot%5D/events{/privacy}",
                  "received_events_url": "https://api.github.com/users/github-actions%5Bbot%5D/received_events",
                  "type": "Bot",
                  "user_view_type": "public",
                  "site_admin": false
                },
                "content_type": "application/zip",
                "state": "uploaded",
                "size": 4026290,
                "digest": "sha256:0333addf9370d73ef51c9a9ef8524a332ad33d1ec3269d027e893b5dc742df96",
                "download_count": 1,
                "created_at": "2025-08-10T16:33:01Z",
                "updated_at": "2025-08-10T16:33:01Z",
                "browser_download_url": "https://github.com/deezer/caupain/releases/download/v1.5.0/caupain-1.5.0-windows.zip"
              },
              {
                "url": "https://api.github.com/repos/deezer/caupain/releases/assets/281172770",
                "id": 281172770,
                "node_id": "RA_kwDOOYhowc4Qwlsi",
                "name": "caupain_1.5.0-1_amd64.deb",
                "label": "",
                "uploader": {
                  "login": "github-actions[bot]",
                  "id": 41898282,
                  "node_id": "MDM6Qm90NDE4OTgyODI=",
                  "avatar_url": "https://avatars.githubusercontent.com/in/15368?v=4",
                  "gravatar_id": "",
                  "url": "https://api.github.com/users/github-actions%5Bbot%5D",
                  "html_url": "https://github.com/apps/github-actions",
                  "followers_url": "https://api.github.com/users/github-actions%5Bbot%5D/followers",
                  "following_url": "https://api.github.com/users/github-actions%5Bbot%5D/following{/other_user}",
                  "gists_url": "https://api.github.com/users/github-actions%5Bbot%5D/gists{/gist_id}",
                  "starred_url": "https://api.github.com/users/github-actions%5Bbot%5D/starred{/owner}{/repo}",
                  "subscriptions_url": "https://api.github.com/users/github-actions%5Bbot%5D/subscriptions",
                  "organizations_url": "https://api.github.com/users/github-actions%5Bbot%5D/orgs",
                  "repos_url": "https://api.github.com/users/github-actions%5Bbot%5D/repos",
                  "events_url": "https://api.github.com/users/github-actions%5Bbot%5D/events{/privacy}",
                  "received_events_url": "https://api.github.com/users/github-actions%5Bbot%5D/received_events",
                  "type": "Bot",
                  "user_view_type": "public",
                  "site_admin": false
                },
                "content_type": "application/x-debian-package",
                "state": "uploaded",
                "size": 6245874,
                "digest": "sha256:8f84a514d908dbe620bea1cb5ec2854a6abf2b43fe9560fde9178d1bb6ddf718",
                "download_count": 1,
                "created_at": "2025-08-10T16:33:01Z",
                "updated_at": "2025-08-10T16:33:01Z",
                "browser_download_url": "https://github.com/deezer/caupain/releases/download/v1.5.0/caupain_1.5.0-1_amd64.deb"
              },
              {
                "url": "https://api.github.com/repos/deezer/caupain/releases/assets/281172768",
                "id": 281172768,
                "node_id": "RA_kwDOOYhowc4Qwlsg",
                "name": "caupain_1.5.0-1_arm64.deb",
                "label": "",
                "uploader": {
                  "login": "github-actions[bot]",
                  "id": 41898282,
                  "node_id": "MDM6Qm90NDE4OTgyODI=",
                  "avatar_url": "https://avatars.githubusercontent.com/in/15368?v=4",
                  "gravatar_id": "",
                  "url": "https://api.github.com/users/github-actions%5Bbot%5D",
                  "html_url": "https://github.com/apps/github-actions",
                  "followers_url": "https://api.github.com/users/github-actions%5Bbot%5D/followers",
                  "following_url": "https://api.github.com/users/github-actions%5Bbot%5D/following{/other_user}",
                  "gists_url": "https://api.github.com/users/github-actions%5Bbot%5D/gists{/gist_id}",
                  "starred_url": "https://api.github.com/users/github-actions%5Bbot%5D/starred{/owner}{/repo}",
                  "subscriptions_url": "https://api.github.com/users/github-actions%5Bbot%5D/subscriptions",
                  "organizations_url": "https://api.github.com/users/github-actions%5Bbot%5D/orgs",
                  "repos_url": "https://api.github.com/users/github-actions%5Bbot%5D/repos",
                  "events_url": "https://api.github.com/users/github-actions%5Bbot%5D/events{/privacy}",
                  "received_events_url": "https://api.github.com/users/github-actions%5Bbot%5D/received_events",
                  "type": "Bot",
                  "user_view_type": "public",
                  "site_admin": false
                },
                "content_type": "application/x-debian-package",
                "state": "uploaded",
                "size": 6332602,
                "digest": "sha256:3540787740e2cc6e586721286404c62fd8009fafce98ad29f88588b6a47f9e3e",
                "download_count": 1,
                "created_at": "2025-08-10T16:33:01Z",
                "updated_at": "2025-08-10T16:33:01Z",
                "browser_download_url": "https://github.com/deezer/caupain/releases/download/v1.5.0/caupain_1.5.0-1_arm64.deb"
              }
            ],
            "tarball_url": "https://api.github.com/repos/deezer/caupain/tarball/v1.5.0",
            "zipball_url": "https://api.github.com/repos/deezer/caupain/zipball/v1.5.0",
            "body": "### Added\n\n- Possibility to replace versions directly in the catalog file (#49)\n\n### Changed\n\n- Updated Gradle to 9.0.0. **Important:** the Caupain Gradle plugin now requires Gradle 9.0.0 as its minimum version.\n",
            "reactions": {
              "url": "https://api.github.com/repos/deezer/caupain/releases/238858162/reactions",
              "total_count": 1,
              "+1": 0,
              "-1": 0,
              "laugh": 0,
              "hooray": 1,
              "confused": 0,
              "heart": 0,
              "rocket": 0,
              "eyes": 0
            }
          }
        ]"""

private val REPO_GITHUB_URL =
    Url("https://api.github.com/repos/$OWNER/$CHANGELOG_REPO")

private const val REPO = """{
  "id": 965241025,
  "node_id": "R_kgDOOYhowQ",
  "name": "caupain",
  "full_name": "deezer/caupain",
  "private": false,
  "owner": {
    "login": "deezer",
    "id": 4393583,
    "node_id": "MDEyOk9yZ2FuaXphdGlvbjQzOTM1ODM=",
    "avatar_url": "https://avatars.githubusercontent.com/u/4393583?v=4",
    "gravatar_id": "",
    "url": "https://api.github.com/users/deezer",
    "html_url": "https://github.com/deezer",
    "followers_url": "https://api.github.com/users/deezer/followers",
    "following_url": "https://api.github.com/users/deezer/following{/other_user}",
    "gists_url": "https://api.github.com/users/deezer/gists{/gist_id}",
    "starred_url": "https://api.github.com/users/deezer/starred{/owner}{/repo}",
    "subscriptions_url": "https://api.github.com/users/deezer/subscriptions",
    "organizations_url": "https://api.github.com/users/deezer/orgs",
    "repos_url": "https://api.github.com/users/deezer/repos",
    "events_url": "https://api.github.com/users/deezer/events{/privacy}",
    "received_events_url": "https://api.github.com/users/deezer/received_events",
    "type": "Organization",
    "user_view_type": "public",
    "site_admin": false
  },
  "html_url": "https://github.com/deezer/caupain",
  "description": "Your best buddy for keeping versions catalogs up to date!",
  "fork": false,
  "url": "https://api.github.com/repos/deezer/caupain",
  "forks_url": "https://api.github.com/repos/deezer/caupain/forks",
  "keys_url": "https://api.github.com/repos/deezer/caupain/keys{/key_id}",
  "collaborators_url": "https://api.github.com/repos/deezer/caupain/collaborators{/collaborator}",
  "teams_url": "https://api.github.com/repos/deezer/caupain/teams",
  "hooks_url": "https://api.github.com/repos/deezer/caupain/hooks",
  "issue_events_url": "https://api.github.com/repos/deezer/caupain/issues/events{/number}",
  "events_url": "https://api.github.com/repos/deezer/caupain/events",
  "assignees_url": "https://api.github.com/repos/deezer/caupain/assignees{/user}",
  "branches_url": "https://api.github.com/repos/deezer/caupain/branches{/branch}",
  "tags_url": "https://api.github.com/repos/deezer/caupain/tags",
  "blobs_url": "https://api.github.com/repos/deezer/caupain/git/blobs{/sha}",
  "git_tags_url": "https://api.github.com/repos/deezer/caupain/git/tags{/sha}",
  "git_refs_url": "https://api.github.com/repos/deezer/caupain/git/refs{/sha}",
  "trees_url": "https://api.github.com/repos/deezer/caupain/git/trees{/sha}",
  "statuses_url": "https://api.github.com/repos/deezer/caupain/statuses/{sha}",
  "languages_url": "https://api.github.com/repos/deezer/caupain/languages",
  "stargazers_url": "https://api.github.com/repos/deezer/caupain/stargazers",
  "contributors_url": "https://api.github.com/repos/deezer/caupain/contributors",
  "subscribers_url": "https://api.github.com/repos/deezer/caupain/subscribers",
  "subscription_url": "https://api.github.com/repos/deezer/caupain/subscription",
  "commits_url": "https://api.github.com/repos/deezer/caupain/commits{/sha}",
  "git_commits_url": "https://api.github.com/repos/deezer/caupain/git/commits{/sha}",
  "comments_url": "https://api.github.com/repos/deezer/caupain/comments{/number}",
  "issue_comment_url": "https://api.github.com/repos/deezer/caupain/issues/comments{/number}",
  "contents_url": "https://api.github.com/repos/deezer/caupain/contents/{+path}",
  "compare_url": "https://api.github.com/repos/deezer/caupain/compare/{base}...{head}",
  "merges_url": "https://api.github.com/repos/deezer/caupain/merges",
  "archive_url": "https://api.github.com/repos/deezer/caupain/{archive_format}{/ref}",
  "downloads_url": "https://api.github.com/repos/deezer/caupain/downloads",
  "issues_url": "https://api.github.com/repos/deezer/caupain/issues{/number}",
  "pulls_url": "https://api.github.com/repos/deezer/caupain/pulls{/number}",
  "milestones_url": "https://api.github.com/repos/deezer/caupain/milestones{/number}",
  "notifications_url": "https://api.github.com/repos/deezer/caupain/notifications{?since,all,participating}",
  "labels_url": "https://api.github.com/repos/deezer/caupain/labels{/name}",
  "releases_url": "https://api.github.com/repos/deezer/caupain/releases{/id}",
  "deployments_url": "https://api.github.com/repos/deezer/caupain/deployments",
  "created_at": "2025-04-12T18:10:42Z",
  "updated_at": "2026-01-25T14:29:37Z",
  "pushed_at": "2026-01-26T09:26:05Z",
  "git_url": "git://github.com/deezer/caupain.git",
  "ssh_url": "git@github.com:deezer/caupain.git",
  "clone_url": "https://github.com/deezer/caupain.git",
  "svn_url": "https://github.com/deezer/caupain",
  "homepage": "",
  "size": 12291,
  "stargazers_count": 138,
  "watchers_count": 138,
  "language": "Kotlin",
  "has_issues": true,
  "has_projects": false,
  "has_downloads": true,
  "has_wiki": false,
  "has_pages": true,
  "has_discussions": true,
  "forks_count": 7,
  "mirror_url": null,
  "archived": false,
  "disabled": false,
  "open_issues_count": 1,
  "license": {
    "key": "mit",
    "name": "MIT License",
    "spdx_id": "MIT",
    "url": "https://api.github.com/licenses/mit",
    "node_id": "MDc6TGljZW5zZTEz"
  },
  "allow_forking": true,
  "is_template": false,
  "web_commit_signoff_required": false,
  "topics": [
    "dependencies-checking",
    "gradle",
    "kotlin"
  ],
  "visibility": "public",
  "forks": 7,
  "open_issues": 1,
  "watchers": 138,
  "default_branch": "main",
  "temp_clone_token": null,
  "custom_properties": {},
  "organization": {
    "login": "deezer",
    "id": 4393583,
    "node_id": "MDEyOk9yZ2FuaXphdGlvbjQzOTM1ODM=",
    "avatar_url": "https://avatars.githubusercontent.com/u/4393583?v=4",
    "gravatar_id": "",
    "url": "https://api.github.com/users/deezer",
    "html_url": "https://github.com/deezer",
    "followers_url": "https://api.github.com/users/deezer/followers",
    "following_url": "https://api.github.com/users/deezer/following{/other_user}",
    "gists_url": "https://api.github.com/users/deezer/gists{/gist_id}",
    "starred_url": "https://api.github.com/users/deezer/starred{/owner}{/repo}",
    "subscriptions_url": "https://api.github.com/users/deezer/subscriptions",
    "organizations_url": "https://api.github.com/users/deezer/orgs",
    "repos_url": "https://api.github.com/users/deezer/repos",
    "events_url": "https://api.github.com/users/deezer/events{/privacy}",
    "received_events_url": "https://api.github.com/users/deezer/received_events",
    "type": "Organization",
    "user_view_type": "public",
    "site_admin": false
  },
  "network_count": 7,
  "subscribers_count": 1
}"""

private val TREES_GITHUB_URL =
    Url("https://api.github.com/repos/$OWNER/$CHANGELOG_REPO/git/trees/main")

private const val TREES = """{
  "sha": "c9f0fe79ee10c9fc140b8fa1ca11cc45e07ed28b",
  "url": "https://api.github.com/repos/deezer/caupain/git/trees/c9f0fe79ee10c9fc140b8fa1ca11cc45e07ed28b",
  "tree": [
    {
      "path": ".github",
      "mode": "040000",
      "type": "tree",
      "sha": "456865aff32ca6013355a5830dd33863fab9f209",
      "url": "https://api.github.com/repos/deezer/caupain/git/trees/456865aff32ca6013355a5830dd33863fab9f209"
    },
    {
      "path": ".gitignore",
      "mode": "100644",
      "type": "blob",
      "sha": "7d9c0e482c1b0bcc4826668dec9d880c9496ca9a",
      "size": 315,
      "url": "https://api.github.com/repos/deezer/caupain/git/blobs/7d9c0e482c1b0bcc4826668dec9d880c9496ca9a"
    },
    {
      "path": "CHANGELOG.md",
      "mode": "100644",
      "type": "blob",
      "sha": "258ed31a7e68f9eab427acaae82796405cf1dfce",
      "size": 4352,
      "url": "https://api.github.com/repos/deezer/caupain/git/blobs/258ed31a7e68f9eab427acaae82796405cf1dfce"
    },
    {
      "path": "CODE_OF_CONDUCT.md",
      "mode": "100644",
      "type": "blob",
      "sha": "3b04f66d98dc620e7e8552f13c41a03d1c9b7877",
      "size": 5224,
      "url": "https://api.github.com/repos/deezer/caupain/git/blobs/3b04f66d98dc620e7e8552f13c41a03d1c9b7877"
    },
    {
      "path": "CONTRIBUTING.md",
      "mode": "100644",
      "type": "blob",
      "sha": "3e4f31b285e7646a1d8d66ca54590992ba1b3af8",
      "size": 4255,
      "url": "https://api.github.com/repos/deezer/caupain/git/blobs/3e4f31b285e7646a1d8d66ca54590992ba1b3af8"
    },
    {
      "path": "LICENSE",
      "mode": "100644",
      "type": "blob",
      "sha": "740c8cb6747deb2ce660031446756955f72958c6",
      "size": 1063,
      "url": "https://api.github.com/repos/deezer/caupain/git/blobs/740c8cb6747deb2ce660031446756955f72958c6"
    },
    {
      "path": "README.md",
      "mode": "100644",
      "type": "blob",
      "sha": "cde81d09e7e04e31caaf3002c55386773eefc4ce",
      "size": 2576,
      "url": "https://api.github.com/repos/deezer/caupain/git/blobs/cde81d09e7e04e31caaf3002c55386773eefc4ce"
    },
    {
      "path": "build.gradle.kts",
      "mode": "100644",
      "type": "blob",
      "sha": "df6baa3e668dd47b15f77589ecacc2fc9b56455f",
      "size": 3004,
      "url": "https://api.github.com/repos/deezer/caupain/git/blobs/df6baa3e668dd47b15f77589ecacc2fc9b56455f"
    },
    {
      "path": "buildSrc",
      "mode": "040000",
      "type": "tree",
      "sha": "18fd4ca5e3b2f73b3e2dab62a57f05e3b65fb360",
      "url": "https://api.github.com/repos/deezer/caupain/git/trees/18fd4ca5e3b2f73b3e2dab62a57f05e3b65fb360"
    },
    {
      "path": "caupain.toml",
      "mode": "100644",
      "type": "blob",
      "sha": "9734d44b8ece571ff7584b1ea6b52480d6eaf4c1",
      "size": 262,
      "url": "https://api.github.com/repos/deezer/caupain/git/blobs/9734d44b8ece571ff7584b1ea6b52480d6eaf4c1"
    },
    {
      "path": "cli",
      "mode": "040000",
      "type": "tree",
      "sha": "3ff6877e72c9194fb27d6cb2a021d956a0f06f58",
      "url": "https://api.github.com/repos/deezer/caupain/git/trees/3ff6877e72c9194fb27d6cb2a021d956a0f06f58"
    },
    {
      "path": "code-quality",
      "mode": "040000",
      "type": "tree",
      "sha": "56fa043f74130fdf04c4f297698715c3a0aabac9",
      "url": "https://api.github.com/repos/deezer/caupain/git/trees/56fa043f74130fdf04c4f297698715c3a0aabac9"
    },
    {
      "path": "core",
      "mode": "040000",
      "type": "tree",
      "sha": "2e9dc70691792b7aea3a4ba918f9f1d3bb16090e",
      "url": "https://api.github.com/repos/deezer/caupain/git/trees/2e9dc70691792b7aea3a4ba918f9f1d3bb16090e"
    },
    {
      "path": "demo_cli.svg",
      "mode": "100644",
      "type": "blob",
      "sha": "6a89277328a4a6fa046e1e72780c020ddcc98d5a",
      "size": 313808,
      "url": "https://api.github.com/repos/deezer/caupain/git/blobs/6a89277328a4a6fa046e1e72780c020ddcc98d5a"
    },
    {
      "path": "demo_console.png",
      "mode": "100644",
      "type": "blob",
      "sha": "e0a2192b975c496e59022762f19904d35bc4d6f3",
      "size": 127499,
      "url": "https://api.github.com/repos/deezer/caupain/git/blobs/e0a2192b975c496e59022762f19904d35bc4d6f3"
    },
    {
      "path": "demo_html.png",
      "mode": "100644",
      "type": "blob",
      "sha": "642509d7b8fcad41de9194900cd323db1db819c8",
      "size": 145846,
      "url": "https://api.github.com/repos/deezer/caupain/git/blobs/642509d7b8fcad41de9194900cd323db1db819c8"
    },
    {
      "path": "demo_md.png",
      "mode": "100644",
      "type": "blob",
      "sha": "1fcf16c34c93af1ea6ca1be0409f734608c70d45",
      "size": 37181,
      "url": "https://api.github.com/repos/deezer/caupain/git/blobs/1fcf16c34c93af1ea6ca1be0409f734608c70d45"
    },
    {
      "path": "generateCompletions.sh",
      "mode": "100755",
      "type": "blob",
      "sha": "ac2711d496b2c0b7dd095110cbca6c239a38cea5",
      "size": 177,
      "url": "https://api.github.com/repos/deezer/caupain/git/blobs/ac2711d496b2c0b7dd095110cbca6c239a38cea5"
    },
    {
      "path": "gradle-plugin",
      "mode": "040000",
      "type": "tree",
      "sha": "a7769e21bc22839d06db5785b11952ccf97a32dc",
      "url": "https://api.github.com/repos/deezer/caupain/git/trees/a7769e21bc22839d06db5785b11952ccf97a32dc"
    },
    {
      "path": "gradle.properties",
      "mode": "100644",
      "type": "blob",
      "sha": "a03b4f75fa6d18816cebfa7053a463c4e8fe7e00",
      "size": 470,
      "url": "https://api.github.com/repos/deezer/caupain/git/blobs/a03b4f75fa6d18816cebfa7053a463c4e8fe7e00"
    },
    {
      "path": "gradle",
      "mode": "040000",
      "type": "tree",
      "sha": "9746f202624039ab3cd95cd0472b99a671bb3abd",
      "url": "https://api.github.com/repos/deezer/caupain/git/trees/9746f202624039ab3cd95cd0472b99a671bb3abd"
    },
    {
      "path": "gradlew",
      "mode": "100755",
      "type": "blob",
      "sha": "f5feea6d6b116baaca5a2642d4d9fa1f47d574a7",
      "size": 8762,
      "url": "https://api.github.com/repos/deezer/caupain/git/blobs/f5feea6d6b116baaca5a2642d4d9fa1f47d574a7"
    },
    {
      "path": "gradlew.bat",
      "mode": "100644",
      "type": "blob",
      "sha": "9d21a21834d5195c278ba17baec3115b2aaab06e",
      "size": 2872,
      "url": "https://api.github.com/repos/deezer/caupain/git/blobs/9d21a21834d5195c278ba17baec3115b2aaab06e"
    },
    {
      "path": "install.sh",
      "mode": "100755",
      "type": "blob",
      "sha": "579efa00e89ed8e593cb08b678da7d9cff32cb5e",
      "size": 148,
      "url": "https://api.github.com/repos/deezer/caupain/git/blobs/579efa00e89ed8e593cb08b678da7d9cff32cb5e"
    },
    {
      "path": "kotlin-js-store",
      "mode": "040000",
      "type": "tree",
      "sha": "bbed79f68fc4df355532dd5b41948373560d6fda",
      "url": "https://api.github.com/repos/deezer/caupain/git/trees/bbed79f68fc4df355532dd5b41948373560d6fda"
    },
    {
      "path": "sample-plugin-policy-jar",
      "mode": "040000",
      "type": "tree",
      "sha": "42341f371e66295a65b0e1c36a9eecff7a4266e6",
      "url": "https://api.github.com/repos/deezer/caupain/git/trees/42341f371e66295a65b0e1c36a9eecff7a4266e6"
    },
    {
      "path": "settings.gradle.kts",
      "mode": "100644",
      "type": "blob",
      "sha": "4fc35143beb2e5c2c6f5863a3a2225d168c15ff0",
      "size": 358,
      "url": "https://api.github.com/repos/deezer/caupain/git/blobs/4fc35143beb2e5c2c6f5863a3a2225d168c15ff0"
    }
  ],
  "truncated": false
}"""

private val MAVEN_INFO_RELEASE = MavenInfo(
    scm = SCMInfos(
        url = "http://github.com/$OWNER/$RELEASE_REPO"
    )
)

private val MAVEN_INFO_CHANGELOG = MavenInfo(
    scm = SCMInfos(
        url = "git@github.com/$OWNER/$CHANGELOG_REPO"
    )
)

private val MAVEN_INFO_NO_GITHUB = MavenInfo(
    scm = SCMInfos(
        url = "http://www.example.com/repo"
    )
)

private class TestException : IOException()
