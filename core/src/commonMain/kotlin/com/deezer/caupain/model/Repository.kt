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

package com.deezer.caupain.model

import com.deezer.caupain.Serializable
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Maven repository
 *
 * @property url The URL of the repository.
 * @property user The username for authentication (optional).
 * @property password The password for authentication (optional).
 */
public class Repository(
    public val url: String,
    public val user: String?,
    public val password: String?
) : Serializable {
    public constructor(url: String) : this(url, null, null)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Repository

        if (url != other.url) return false
        if (user != other.user) return false
        if (password != other.password) return false

        return true
    }

    override fun hashCode(): Int {
        var result = url.hashCode()
        result = 31 * result + (user?.hashCode() ?: 0)
        result = 31 * result + (password?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "Repository(user=$user, url='$url', password=$password)"
    }

    public companion object {
        private const val serialVersionUID = 1L
    }
}

@OptIn(ExperimentalEncodingApi::class)
internal suspend fun HttpClient.executeRepositoryRequest(
    repository: Repository,
    urlBuilder: URLBuilder.() -> Unit = {}
) = get(repository.url) {
    url(urlBuilder)
    if (repository.user != null && repository.password != null) {
        header(
            HttpHeaders.Authorization,
            "Basic ${Base64.encode("${repository.user}:${repository.password}".encodeToByteArray())}"
        )
    }
}

/**
 * Default repositories for dependency resolution.
 */
public object DefaultRepositories {
    /**
     * Represents the Google Maven repository.
     */
    public val google: Repository = Repository("https://dl.google.com/dl/android/maven2")

    /**
     * Represents the Maven Central repository.
     */
    public val mavenCentral: Repository = Repository("https://repo.maven.apache.org/maven2")

    /**
     * Represents the Gradle Plugin repository.
     */
    public val gradlePlugins: Repository = Repository("https://plugins.gradle.org/m2")
}
