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
@file:JvmName("Repositories")

package com.deezer.caupain.model

import com.deezer.caupain.Serializable
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads

/**
 * Maven repository
 *
 * @property url The URL of the repository.
 * @property credentials The credentials to use for authentication with the repository.
 */
public interface Repository : Serializable {
    public val url: String
    public val credentials: Credentials?
    @Deprecated("Use credentials instead")
    public val user: String?
        get() = (credentials as? PasswordCredentials)?.user
    @Deprecated("Use credentials instead")
    public val password: String?
        get() = (credentials as? PasswordCredentials)?.password

    /**
     * Checks if the given [Dependency] is accepted by this repository.
     */
    public operator fun contains(dependency: Dependency): Boolean
}

/**
 * Represents credentials for accessing a repository.
 * Implementations should provide a way to configure the HTTP request with the necessary authentication headers.
 */
public interface Credentials : Serializable {
    /**
     * Configures the HTTP request with the necessary authentication headers.
     *
     * @receiver the [HttpRequestBuilder] to configure.
     */
    public fun HttpRequestBuilder.configureAuthentication()
}

/**
 * Represents credentials for accessing a repository using a username and password.
 * The credentials are encoded in Base64 and added to the HTTP request as a Basic Authentication header.
 *
 * @property user The username for authentication.
 * @property password The password for authentication.
 */
@OptIn(ExperimentalEncodingApi::class)
public class PasswordCredentials(
    public val user: String,
    public val password: String
) : Credentials {
    override fun HttpRequestBuilder.configureAuthentication() {
        header(
            HttpHeaders.Authorization,
            "Basic ${Base64.encode("$user:$password".encodeToByteArray())}"
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as PasswordCredentials

        if (user != other.user) return false
        if (password != other.password) return false

        return true
    }

    override fun hashCode(): Int {
        var result = user.hashCode()
        result = 31 * result + password.hashCode()
        return result
    }

    override fun toString(): String {
        return "PasswordCredentials(user='$user', password='$password')"
    }
}

/**
 * Represents credentials for accessing a repository using a custom header.
 * The header is added to the HTTP request as a key-value pair.
 *
 * @property name The name of the header.
 * @property value The value of the header.
 */
public class HeaderCredentials(
    public val name: String,
    public val value: String
) : Credentials {
    override fun HttpRequestBuilder.configureAuthentication() {
        header(name, value)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as HeaderCredentials

        if (name != other.name) return false
        if (value != other.value) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + value.hashCode()
        return result
    }

    override fun toString(): String {
        return "HeaderCredentials(name='$name', value='$value')"
    }
}

/**
 * Creates a [Repository] with the given URL, user, password, and optional [ComponentFilter].
 */
@JvmOverloads
public fun Repository(
    url: String,
    credentials: Credentials?,
    componentFilter: ComponentFilter? = null
): Repository = DefaultRepository(url, credentials, componentFilter)

/**
 * Creates a [Repository] with the given URL, user, password, and optional [ComponentFilter].
 */
@JvmOverloads
public fun Repository(
    url: String,
    user: String?,
    password: String?,
    componentFilter: ComponentFilter? = null
): Repository = Repository(
    url = url,
    credentials = if (user == null || password == null) null else PasswordCredentials(user, password),
    componentFilter = componentFilter
)

/**
 * Creates a [Repository] with the given URL and optional [ComponentFilter].
 */
@JvmOverloads
public fun Repository(
    url: String,
    componentFilter: ComponentFilter? = null
): Repository = Repository(url, null, componentFilter)

/**
 * Returns a repository with the given [ComponentFilter] applied.
 *
 * @see ComponentFilterBuilder.includes
 * @see ComponentFilterBuilder.excludes
 */
public inline fun Repository.withComponentFilter(builder: ComponentFilterBuilder.() -> Unit): Repository {
    return Repository(
        url = url,
        credentials = credentials,
        componentFilter = buildComponentFilter(builder)
    )
}

internal data class DefaultRepository(
    override val url: String,
    override val credentials: Credentials?,
    private val componentFilter: ComponentFilter? = null
) : Repository {

    override operator fun contains(dependency: Dependency): Boolean {
        return componentFilter == null || componentFilter.accepts(dependency)
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Represents a filter for components in a repository. If used in the [Repository], only the [Dependency]
 * that match the filter will be checked against the given [Repository].
 */
public interface ComponentFilter : Serializable {
    /**
     * Checks if the given [Dependency] is accepted by this filter.
     *
     * @param dependency The dependency to check.
     * @return `true` if the dependency is accepted, `false` otherwise.
     */
    public fun accepts(dependency: Dependency): Boolean
}

/**
 * Builder for [ComponentFilter]. If only excludes are defined, then all dependencies except those
 * excluded will be accepted. If only includes are defined, then only those dependencies will be accepted.
 * If both are defined, then only those dependencies that are included and not excluded will be accepted.
 */
public class ComponentFilterBuilder {
    private val includes = mutableListOf<PackageSpec>()
    private val excludes = mutableListOf<PackageSpec>()

    /**
     * Excludes a dependency from this repository. If name is null, group is used as a glob, with the following rules:
     * - `?`: Wildcard that matches exactly one character, other than `.`
     * - `*`: wildcard that matches zero, one or multiple characters, other than `.`
     * - `**`: Wildcard that matches zero, one or multiple packages. For example, `**.sub.name` matches
     * `com.example.sub.name`, `com.example.sub.sub.name`. `**` must be either preceded by `.` or be at
     * the beginning of the glob. `**` must be either followed by `.` or be at the end of the glob.
     * If the glob only consist of a `**`, it will be a match for everything.
     *
     * @property group The group to exclude. If `name` is null, then this is interpreted as a glob
     * @property name The name to exclude. If null, all libraries in the group are excluded.
     */
    @JvmOverloads
    public fun exclude(group: String, name: String? = null): ComponentFilterBuilder {
        excludes.add(PackageSpec(group, name))
        return this
    }

    /**
     * Includes a dependency in this repository. If name is null, group is used as a glob, with the following rules:
     * - `?`: Wildcard that matches exactly one character, other than `.`
     * - `*`: wildcard that matches zero, one or multiple characters, other than `.`
     * - `**`: Wildcard that matches zero, one or multiple packages. For example, `**.sub.name` matches
     * `com.example.sub.name`, `com.example.sub.sub.name`. `**` must be either preceded by `.` or be at
     * the beginning of the glob. `**` must be either followed by `.` or be at the end of the glob.
     * If the glob only consist of a `**`, it will be a match for everything.
     *
     * @property group The group to include. If `name` is null, then this is interpreted as a glob
     * @property name The name to included. If null, all libraries in the group are included.
     */
    @JvmOverloads
    public fun include(group: String, name: String? = null): ComponentFilterBuilder {
        includes.add(PackageSpec(group, name))
        return this
    }

    /**
     * Builds the [ComponentFilter] with the current includes and excludes.
     */
    public fun build(): ComponentFilter {
        return DefaultComponentFilter(includes.toList(), excludes.toList())
    }
}

/**
 * Builds a [ComponentFilter] using the given builder function.
 *
 * @see ComponentFilterBuilder.includes
 * @see ComponentFilterBuilder.excludes
 */
public inline fun buildComponentFilter(builder: ComponentFilterBuilder.() -> Unit): ComponentFilter {
    return ComponentFilterBuilder().apply(builder).build()
}

internal data class DefaultComponentFilter(
    private val includes: List<PackageSpec>,
    private val excludes: List<PackageSpec>,
) : ComponentFilter {
    override fun accepts(dependency: Dependency): Boolean {
        if (excludes.isNotEmpty()) {
            if (excludes.any { it.matches(dependency) }) return false
        }
        if (includes.isNotEmpty()) {
            if (includes.none { it.matches(dependency) }) return false
        }
        return true
    }
}

@OptIn(ExperimentalEncodingApi::class)
internal suspend fun HttpClient.executeRepositoryRequest(
    repository: Repository,
    urlBuilder: URLBuilder.() -> Unit = {}
): HttpResponse = get(repository.url) {
    url(urlBuilder)
    repository.credentials?.run { configureAuthentication() }
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
