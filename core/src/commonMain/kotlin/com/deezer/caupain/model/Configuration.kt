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
@file:JvmName("Configurations")

package com.deezer.caupain.model

import com.deezer.caupain.Serializable
import com.deezer.caupain.model.gradle.GradleStabilityLevel
import com.deezer.caupain.policies.StabilityLevelPolicy
import dev.drewhamilton.poko.Poko
import okio.Path
import okio.Path.Companion.toPath
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads

/**
 * Configuration is a data class that holds the configuration for the dependency update process.
 *
 * @property repositories A list of repositories to search for dependencies.
 * @property pluginRepositories A list of repositories to search for plugin updates.
 * @property versionCatalogPaths The paths to the version catalog files.
 * @property excludedKeys A set of keys to exclude from the update process.
 * @property excludedLibraries A list of libraries to exclude from the update process.
 * @property excludedPlugins A list of plugins to exclude from the update process.
 * @property policies The policies to use for the update process.
 * @property policyPluginsDir The directory for the policy plugins.
 * @property cacheDir The directory for the HTTP cache.
 * @property cleanCache Whether to clean the cache before running the update process.
 * @property debugHttpCalls Whether to enable debug logging for HTTP calls.
 * @property onlyCheckStaticVersions Whether to only check updates for direct versions or all versions.
 * @property gradleStabilityLevel The desired stability level of the Gradle version.
 * @property checkIgnored Whether to check ignored dependencies or not (they will be placed in a specific
 * property in the result to eventually display).
 * @property searchReleaseNote whether to try to find the release note URL for the updated dependencies.
 * This only works if the source project for the dependency is hosted on GitHub
 * @property githubToken The GitHub token to use for API requests, if any.
 * @property verifyExistence Whether to verify that .pom files exist in the repository before accepting a version
 * as valid.
 */
public interface Configuration : Serializable {
    public val repositories: List<Repository>
    public val pluginRepositories: List<Repository>

    @Deprecated("Use versionCatalogPaths instead")
    public val versionCatalogPath: Path
    public val versionCatalogPaths: Iterable<Path>
    public val excludedKeys: Set<String>
    public val excludedLibraries: List<LibraryExclusion>
    public val excludedPlugins: List<PluginExclusion>

    @Deprecated("Use policies instead")
    public val policy: String
    public val policies: Iterable<String>
    public val policyPluginsDir: Path?
    public val cacheDir: Path?
    public val cleanCache: Boolean
    public val debugHttpCalls: Boolean
    public val onlyCheckStaticVersions: Boolean
    public val gradleStabilityLevel: GradleStabilityLevel
    public val checkIgnored: Boolean
    public val searchReleaseNote: Boolean
    public val githubToken: String?
    public val verifyExistence: Boolean

    public companion object {
        private const val serialVersionUID = 1L

        public val DEFAULT_REPOSITORIES: List<Repository> = listOf(
            DefaultRepositories.mavenCentral,
            DefaultRepositories.google,
        )

        public val DEFAULT_PLUGIN_REPOSITORIES: List<Repository> = listOf(
            DefaultRepositories.gradlePlugins,
            DefaultRepositories.mavenCentral,
            DefaultRepositories.google,
        )

        public val DEFAULT_CATALOG_PATH: Path = "gradle/libs.versions.toml".toPath()
    }
}

/**
 * Creates a new Configuration instance with the specified parameters.
 *
 * @param repositories A list of repositories to search for dependencies.
 * @param pluginRepositories A list of repositories to search for plugin updates.
 * @param versionCatalogPaths The paths to the version catalog files.
 * @param excludedKeys A set of keys to exclude from the update process.
 * @param excludedLibraries A list of libraries to exclude from the update process.
 * @param excludedPlugins A list of plugins to exclude from the update process.
 * @param policies The policies to use for the update process.
 * @param policyPluginsDir The directory for the policy plugins.
 * @param cacheDir The directory for the HTTP cache.
 * @param cleanCache Whether to clean the cache before running the update process.
 * @param debugHttpCalls Whether to enable debug logging for HTTP calls.
 * @param onlyCheckStaticVersions Whether to only check updates for static versions or all versions.
 * @param gradleStabilityLevel The desired stability level of the Gradle version.
 * @param checkIgnored Whether to check ignored dependencies or not (they will be placed in a specific
 * property in the result to eventually display).
 * @param githubToken The GitHub token to use for API requests, if any.
 * @param searchReleaseNote whether to try to find the release note URL for the updated dependencies.
 * This only works if the source project for the dependency is hosted on GitHub. This defaults to true
 * if the GitHub token is provided, otherwise false.
 * @param verifyExistence Whether to verify that .pom files exist in the repository before accepting a version
 * as valid.
 */
@Suppress("LongParameterList") // Needed to reflect parameters
@JvmOverloads
public fun Configuration(
    repositories: List<Repository> = Configuration.DEFAULT_REPOSITORIES,
    pluginRepositories: List<Repository> = Configuration.DEFAULT_PLUGIN_REPOSITORIES,
    versionCatalogPaths: Iterable<Path> = listOf(Configuration.DEFAULT_CATALOG_PATH),
    excludedKeys: Set<String> = emptySet(),
    excludedLibraries: List<LibraryExclusion> = emptyList(),
    excludedPlugins: List<PluginExclusion> = emptyList(),
    policies: Iterable<String> = listOf(StabilityLevelPolicy.name),
    policyPluginsDir: Path? = null,
    cacheDir: Path? = null,
    cleanCache: Boolean = false,
    debugHttpCalls: Boolean = false,
    onlyCheckStaticVersions: Boolean = true,
    gradleStabilityLevel: GradleStabilityLevel = GradleStabilityLevel.STABLE,
    checkIgnored: Boolean = false,
    githubToken: String? = null,
    searchReleaseNote: Boolean = githubToken != null,
    verifyExistence: Boolean = false,
): Configuration = ConfigurationImpl(
    repositories = repositories,
    pluginRepositories = pluginRepositories,
    versionCatalogPaths = versionCatalogPaths,
    excludedKeys = excludedKeys,
    excludedLibraries = excludedLibraries,
    excludedPlugins = excludedPlugins,
    policies = policies,
    policyPluginsDir = policyPluginsDir,
    cacheDir = cacheDir,
    cleanCache = cleanCache,
    debugHttpCalls = debugHttpCalls,
    onlyCheckStaticVersions = onlyCheckStaticVersions,
    gradleStabilityLevel = gradleStabilityLevel,
    checkIgnored = checkIgnored,
    searchReleaseNote = searchReleaseNote,
    githubToken = githubToken,
    verifyExistence = verifyExistence
)

internal data class ConfigurationImpl(
    override val repositories: List<Repository> = Configuration.DEFAULT_REPOSITORIES,
    override val pluginRepositories: List<Repository> = Configuration.DEFAULT_PLUGIN_REPOSITORIES,
    override val versionCatalogPaths: Iterable<Path> = listOf("gradle/libs.versions.toml".toPath()),
    override val excludedKeys: Set<String> = emptySet(),
    override val excludedLibraries: List<LibraryExclusion> = emptyList(),
    override val excludedPlugins: List<PluginExclusion> = emptyList(),
    override val policies: Iterable<String> = listOf(StabilityLevelPolicy.name),
    override val policyPluginsDir: Path? = null,
    override val cacheDir: Path? = null,
    override val cleanCache: Boolean = false,
    override val debugHttpCalls: Boolean = false,
    override val onlyCheckStaticVersions: Boolean = true,
    override val gradleStabilityLevel: GradleStabilityLevel = GradleStabilityLevel.STABLE,
    override val checkIgnored: Boolean = false,
    override val searchReleaseNote: Boolean = false,
    override val githubToken: String? = null,
    override val verifyExistence: Boolean = false
) : Configuration {
    @Deprecated("Use versionCatalogPaths instead", ReplaceWith("versionCatalogPaths.first()"))
    override val versionCatalogPath: Path
        get() = versionCatalogPaths.first()

    @Deprecated("Use policies instead", ReplaceWith("policies.first()"))
    override val policy: String
        get() = policies.first()
}

/**
 * Configuration for excluded items
 */
public sealed interface Exclusion<D : Dependency> {

    /**
     * Checks if a dependency is excluded
     */
    public fun isExcluded(dependency: D): Boolean
}

/**
 * Library exclusion info. If name is null, group is used as a glob, with the following rules:
 * - `?`: Wildcard that matches exactly one character, other than `.`
 * - `*`: wildcard that matches zero, one or multiple characters, other than `.`
 * - `**`: Wildcard that matches zero, one or multiple packages. For example, `**.sub.name` matches
 * `com.example.sub.name`, `com.example.sub.sub.name`. `**` must be either preceded by `.` or be at
 * the beginning of the glob. `**` must be either followed by `.` or be at the end of the glob.
 * If the glob only consist of a `**`, it will be a match for everything.
 *
 * @property group The group of the library to exclude. If `name` is null, then this is interpreted as a glob
 * @property name The name of the library to exclude. If null, all libraries in the group are excluded.
 */
@Poko
public class LibraryExclusion(
    public val group: String,
    public val name: String? = null,
) : Exclusion<Dependency.Library>, Serializable {

    private val spec = PackageSpec(group, name)

    override fun isExcluded(dependency: Dependency.Library): Boolean = spec.matches(dependency)

    private companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Wrapper for plugin id exclusion.
 */
@Poko
public class PluginExclusion(public val id: String) : Exclusion<Dependency.Plugin> {

    override fun isExcluded(dependency: Dependency.Plugin): Boolean = dependency.id == id
}

internal fun Configuration.isExcluded(dependencyKey: String, dependency: Dependency): Boolean {
    if (dependencyKey in excludedKeys) return true
    return when (dependency) {
        is Dependency.Library -> excludedLibraries.any { it.isExcluded(dependency) }
        is Dependency.Plugin -> excludedPlugins.any { it.isExcluded(dependency) }
    }
}
