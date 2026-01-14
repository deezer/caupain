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
import com.deezer.caupain.cli.resolver.CLISelfUpdateResolver.Companion.UPDATE_URL
import com.deezer.caupain.model.Logger
import com.deezer.caupain.model.SelfUpdateInfo
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
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
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.intellij.lang.annotations.Language
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SelfUpdateResolverTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private lateinit var fileSystem: FakeFileSystem

    private lateinit var engine: MockEngine

    private lateinit var checker: DependencyUpdateChecker

    private lateinit var logger: Logger

    private lateinit var resolver: CLISelfUpdateResolver

    private var throwIOError = false

    @BeforeTest
    fun setup() {
        fileSystem = FakeFileSystem()
        fileSystem.createDirectory("/etc".toPath())
        fileSystem.write("/etc/debian_version".toPath()) { }
        engine = MockEngine { requestData ->
            handleRequest(this, requestData)
                ?: respond("Not found", HttpStatusCode.NotFound)
        }
        val mockHttpClient = HttpClient(engine) {
            install(ContentNegotiation) {
                json(DefaultJson)
            }
        }
        checker = mock {
            every { httpClient } returns mockHttpClient
        }
        logger = mock(MockMode.autoUnit)
        resolver = CLISelfUpdateResolver(
            logger = logger,
            ioDispatcher = dispatcher,
            fileSystem = fileSystem
        )
    }

    private fun handleRequest(
        scope: MockRequestHandleScope,
        requestData: HttpRequestData
    ): HttpResponseData? {
        if (throwIOError) throw FakeIOException()
        val url = requestData.url
        return if (url.toString() == CLISelfUpdateResolver.UPDATE_URL) {
            scope.respond(
                content = GITHUB_RESPONSE,
                headers = headersOf(HttpHeaders.ContentType, "application/vnd.github+json")
            )
        } else {
            null
        }
    }

    @AfterTest
    fun teardown() {
        engine.close()
        fileSystem.checkNoOpenFiles()
        fileSystem.close()
        throwIOError = false
    }

    @Test
    fun testUpdate() = runTest(dispatcher) {
        assertEquals(
            expected = SelfUpdateInfo(
                currentVersion = BuildConfig.VERSION,
                updatedVersion = "9999999999.99.99",
                sources = listOf(SelfUpdateInfo.Source.GITHUB_RELEASES) + EXPECTED_SOURCES
            ),
            actual = resolver.resolveSelfUpdate(
                checker = checker,
                versionCatalogs = emptyList()
            )
        )
    }

    @Test
    fun testException() = runTest(dispatcher) {
        throwIOError = true
        resolver.resolveSelfUpdate(
            checker = checker,
            versionCatalogs = emptyList()
        )
        verify {
            logger.error(
                message = "Failed to fetch latest release from $UPDATE_URL",
                throwable = any<FakeIOException>()
            )
        }
    }

    companion object {
        val DefaultJson: Json =
            Json {
                encodeDefaults = true
                isLenient = true
                allowSpecialFloatingPointValues = true
                allowStructuredMapKeys = true
                prettyPrint = false
                useArrayPolymorphism = false
                ignoreUnknownKeys = true
            }

        @Language("JSON")
        val GITHUB_RESPONSE = """
        {
          "url": "https://api.github.com/repos/deezer/caupain/releases/220857124",
          "assets_url": "https://api.github.com/repos/deezer/caupain/releases/220857124/assets",
          "upload_url": "https://uploads.github.com/repos/deezer/caupain/releases/220857124/assets{?name,label}",
          "html_url": "https://github.com/deezer/caupain/releases/tag/v1.1.1",
          "id": 220857124,
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
          "node_id": "RE_kwDOOYhowc4NKgMk",
          "tag_name": "v9999999999.99.99",
          "target_commitish": "main",
          "name": "v1.1.1",
          "draft": false,
          "prerelease": false,
          "created_at": "2025-05-25T14:11:23Z",
          "published_at": "2025-05-25T14:26:41Z",
          "assets": [
            {
              "url": "https://api.github.com/repos/deezer/caupain/releases/assets/257974708",
              "id": 257974708,
              "node_id": "RA_kwDOOYhowc4PYGG0",
              "name": "caupain-1.1.1-jvm.zip",
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
              "size": 15876754,
              "digest": null,
              "download_count": 5,
              "created_at": "2025-05-25T14:26:42Z",
              "updated_at": "2025-05-25T14:26:42Z",
              "browser_download_url": "https://github.com/deezer/caupain/releases/download/v1.1.1/caupain-1.1.1-jvm.zip"
            },
            {
              "url": "https://api.github.com/repos/deezer/caupain/releases/assets/257974706",
              "id": 257974706,
              "node_id": "RA_kwDOOYhowc4PYGGy",
              "name": "caupain-1.1.1-linux-arm.zip",
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
              "size": 6353693,
              "digest": null,
              "download_count": 0,
              "created_at": "2025-05-25T14:26:42Z",
              "updated_at": "2025-05-25T14:26:42Z",
              "browser_download_url": "https://github.com/deezer/caupain/releases/download/v1.1.1/caupain-1.1.1-linux-arm.zip"
            },
            {
              "url": "https://api.github.com/repos/deezer/caupain/releases/assets/257974705",
              "id": 257974705,
              "node_id": "RA_kwDOOYhowc4PYGGx",
              "name": "caupain-1.1.1-linux.zip",
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
              "size": 6151835,
              "digest": null,
              "download_count": 3,
              "created_at": "2025-05-25T14:26:42Z",
              "updated_at": "2025-05-25T14:26:42Z",
              "browser_download_url": "https://github.com/deezer/caupain/releases/download/v1.1.1/caupain-1.1.1-linux.zip"
            },
            {
              "url": "https://api.github.com/repos/deezer/caupain/releases/assets/257974704",
              "id": 257974704,
              "node_id": "RA_kwDOOYhowc4PYGGw",
              "name": "caupain-1.1.1-macos-intel.zip",
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
              "size": 3126335,
              "digest": null,
              "download_count": 0,
              "created_at": "2025-05-25T14:26:42Z",
              "updated_at": "2025-05-25T14:26:42Z",
              "browser_download_url": "https://github.com/deezer/caupain/releases/download/v1.1.1/caupain-1.1.1-macos-intel.zip"
            },
            {
              "url": "https://api.github.com/repos/deezer/caupain/releases/assets/257974702",
              "id": 257974702,
              "node_id": "RA_kwDOOYhowc4PYGGu",
              "name": "caupain-1.1.1-macos-silicon.zip",
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
              "size": 3101565,
              "digest": null,
              "download_count": 3,
              "created_at": "2025-05-25T14:26:42Z",
              "updated_at": "2025-05-25T14:26:42Z",
              "browser_download_url": "https://github.com/deezer/caupain/releases/download/v1.1.1/caupain-1.1.1-macos-silicon.zip"
            },
            {
              "url": "https://api.github.com/repos/deezer/caupain/releases/assets/257974703",
              "id": 257974703,
              "node_id": "RA_kwDOOYhowc4PYGGv",
              "name": "caupain-1.1.1-windows.zip",
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
              "size": 3983328,
              "digest": null,
              "download_count": 3,
              "created_at": "2025-05-25T14:26:42Z",
              "updated_at": "2025-05-25T14:26:42Z",
              "browser_download_url": "https://github.com/deezer/caupain/releases/download/v1.1.1/caupain-1.1.1-windows.zip"
            },
            {
              "url": "https://api.github.com/repos/deezer/caupain/releases/assets/257974707",
              "id": 257974707,
              "node_id": "RA_kwDOOYhowc4PYGGz",
              "name": "caupain_1.1.1-1_amd64.deb",
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
              "content_type": "application/octet-stream",
              "state": "uploaded",
              "size": 6154218,
              "digest": null,
              "download_count": 5,
              "created_at": "2025-05-25T14:26:42Z",
              "updated_at": "2025-05-25T14:26:42Z",
              "browser_download_url": "https://github.com/deezer/caupain/releases/download/v1.1.1/caupain_1.1.1-1_amd64.deb"
            },
            {
              "url": "https://api.github.com/repos/deezer/caupain/releases/assets/258160036",
              "id": 258160036,
              "node_id": "RA_kwDOOYhowc4PYzWk",
              "name": "caupain_1.1.1-1_arm64.deb",
              "label": null,
              "uploader": {
                "login": "bishiboosh",
                "id": 186250,
                "node_id": "MDQ6VXNlcjE4NjI1MA==",
                "avatar_url": "https://avatars.githubusercontent.com/u/186250?v=4",
                "gravatar_id": "",
                "url": "https://api.github.com/users/bishiboosh",
                "html_url": "https://github.com/bishiboosh",
                "followers_url": "https://api.github.com/users/bishiboosh/followers",
                "following_url": "https://api.github.com/users/bishiboosh/following{/other_user}",
                "gists_url": "https://api.github.com/users/bishiboosh/gists{/gist_id}",
                "starred_url": "https://api.github.com/users/bishiboosh/starred{/owner}{/repo}",
                "subscriptions_url": "https://api.github.com/users/bishiboosh/subscriptions",
                "organizations_url": "https://api.github.com/users/bishiboosh/orgs",
                "repos_url": "https://api.github.com/users/bishiboosh/repos",
                "events_url": "https://api.github.com/users/bishiboosh/events{/privacy}",
                "received_events_url": "https://api.github.com/users/bishiboosh/received_events",
                "type": "User",
                "user_view_type": "public",
                "site_admin": false
              },
              "content_type": "application/x-deb",
              "state": "uploaded",
              "size": 6355944,
              "digest": null,
              "download_count": 1,
              "created_at": "2025-05-26T06:59:48Z",
              "updated_at": "2025-05-26T06:59:50Z",
              "browser_download_url": "https://github.com/deezer/caupain/releases/download/v1.1.1/caupain_1.1.1-1_arm64.deb"
            }
          ],
          "tarball_url": "https://api.github.com/repos/deezer/caupain/tarball/v1.1.1",
          "zipball_url": "https://api.github.com/repos/deezer/caupain/zipball/v1.1.1",
          "body": "### Added\r\n\r\n- ARM64 support for Linux in CLI\r\n\r\n### Fixed\r\n\r\n- Version parsing for versions with long numbers (#17, thanks to [@chenxiaolong](https://github.com/chenxiaolong))\r\n\r\n",
          "reactions": {
            "url": "https://api.github.com/repos/deezer/caupain/releases/220857124/reactions",
            "total_count": 2,
            "+1": 0,
            "-1": 0,
            "laugh": 0,
            "hooray": 0,
            "confused": 0,
            "heart": 0,
            "rocket": 2,
            "eyes": 0
          }
        }    
        """.trimIndent()
    }

    private class FakeIOException() : IOException()
}
