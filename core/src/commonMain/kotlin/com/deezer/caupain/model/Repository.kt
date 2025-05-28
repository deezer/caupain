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
 * @property user The username for authentication (optional).
 * @property password The password for authentication (optional).
 */
public interface Repository : Serializable {
    public val url: String
    public val user: String?
    public val password: String?

    /**
     * Checks if the given [Dependency] is accepted by this repository.
     */
    public operator fun contains(dependency: Dependency): Boolean
}

/**
 * Creates a [Repository] with the given URL, user, password, and optional [ComponentFilter].
 */
@JvmOverloads
public fun Repository(
    url: String,
    user: String?,
    password: String?,
    componentFilter: ComponentFilter? = null
): Repository = DefaultRepository(url, user, password, componentFilter)

/**
 * Creates a [Repository] with the given URL and optional [ComponentFilter].
 */
@JvmOverloads
public fun Repository(
    url: String,
    componentFilter: ComponentFilter? = null
): Repository = Repository(url, null, null, componentFilter)

/**
 * Returns a repository with the given [ComponentFilter] applied.
 *
 * @see ComponentFilterBuilder.includes
 * @see ComponentFilterBuilder.excludes
 */
public inline fun Repository.withComponentFilter(builder: ComponentFilterBuilder.() -> Unit): Repository {
    return Repository(
        url = url,
        user = user,
        password = password,
        componentFilter = buildComponentFilter(builder)
    )
}

internal data class DefaultRepository(
    override val url: String,
    override val user: String?,
    override val password: String?,
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
