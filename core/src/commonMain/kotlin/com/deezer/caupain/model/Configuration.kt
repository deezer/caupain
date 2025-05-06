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
import okio.Path
import okio.Path.Companion.toPath

/**
 * Configuration is a data class that holds the configuration for the dependency update process.
 *
 * @property repositories A list of repositories to search for dependencies.
 * @property pluginRepositories A list of repositories to search for plugin updates.
 * @property versionCatalogPath The path to the version catalog file.
 * @property excludedKeys A set of keys to exclude from the update process.
 * @property excludedLibraries A list of libraries to exclude from the update process.
 * @property excludedPlugins A list of plugins to exclude from the update process.
 * @property policy The policy to use for the update process.
 * @property policyPluginsDir The directory for the policy plugins.
 * @property cacheDir The directory for the HTTP cache.
 * @property debugHttpCalls Whether or not to enable debug logging for HTTP calls.
 * @property gradleCurrentVersionUrl The URL to check for the current version of Gradle.
 * @property onlyCheckStaticVersions Whether to only check updates for direct versions or all versions.
 */
public interface Configuration : Serializable {
    public val repositories: List<Repository>
    public val pluginRepositories: List<Repository>
    public val versionCatalogPath: Path
    public val excludedKeys: Set<String>
    public val excludedLibraries: List<LibraryExclusion>
    public val excludedPlugins: List<PluginExclusion>
    public val policy: String?
    public val policyPluginsDir: Path?
    public val cacheDir: Path?
    public val debugHttpCalls: Boolean
    public val gradleCurrentVersionUrl: String
    public val onlyCheckStaticVersions: Boolean

    public companion object {
        private const val serialVersionUID = 1L
        public const val DEFAULT_GRADLE_VERSION_URL: String = "https://services.gradle.org/versions/current"
    }
}

/**
 * Creates a new Configuration instance with the specified parameters.
 *
 * @param repositories A list of repositories to search for dependencies.
 * @param pluginRepositories A list of repositories to search for plugin updates.
 * @param versionCatalogPath The path to the version catalog file.
 * @param excludedKeys A set of keys to exclude from the update process.
 * @param excludedLibraries A list of libraries to exclude from the update process.
 * @param excludedPlugins A list of plugins to exclude from the update process.
 * @param policy The policy to use for the update process.
 * @param policyPluginsDir The directory for the policy plugins.
 * @param cacheDir The directory for the HTTP cache.
 * @param debugHttpCalls Whether or not to enable debug logging for HTTP calls.
 * @param gradleCurrentVersionUrl The URL to check for the current version of Gradle.
 * @param onlyCheckStaticVersions Whether to only check updates for static versions or all versions.
 */
@Suppress("LongParameterList") // Needed to reflect parameters
public fun Configuration(
    repositories: List<Repository> = listOf(
        DefaultRepositories.mavenCentral,
        DefaultRepositories.google,
    ),
    pluginRepositories: List<Repository> = listOf(
        DefaultRepositories.gradlePlugins,
        DefaultRepositories.mavenCentral,
        DefaultRepositories.google,
    ),
    versionCatalogPath: Path = "gradle/libs.versions.toml".toPath(),
    excludedKeys: Set<String> = emptySet(),
    excludedLibraries: List<LibraryExclusion> = emptyList(),
    excludedPlugins: List<PluginExclusion> = emptyList(),
    policy: String? = null,
    policyPluginsDir: Path? = null,
    cacheDir: Path? = null,
    debugHttpCalls: Boolean = false,
    gradleCurrentVersionUrl: String = Configuration.DEFAULT_GRADLE_VERSION_URL,
    onlyCheckStaticVersions: Boolean = true,
): Configuration = ConfigurationImpl(
    repositories = repositories,
    pluginRepositories = pluginRepositories,
    versionCatalogPath = versionCatalogPath,
    excludedKeys = excludedKeys,
    excludedLibraries = excludedLibraries,
    excludedPlugins = excludedPlugins,
    policy = policy,
    policyPluginsDir = policyPluginsDir,
    cacheDir = cacheDir,
    debugHttpCalls = debugHttpCalls,
    gradleCurrentVersionUrl = gradleCurrentVersionUrl,
    onlyCheckStaticVersions = onlyCheckStaticVersions
)

internal data class ConfigurationImpl(
    override val repositories: List<Repository> = listOf(
        DefaultRepositories.mavenCentral,
        DefaultRepositories.google,
    ),
    override val pluginRepositories: List<Repository> = listOf(
        DefaultRepositories.gradlePlugins,
        DefaultRepositories.mavenCentral,
        DefaultRepositories.google,
    ),
    override val versionCatalogPath: Path = "gradle/libs.versions.toml".toPath(),
    override val excludedKeys: Set<String> = emptySet(),
    override val excludedLibraries: List<LibraryExclusion> = emptyList(),
    override val excludedPlugins: List<PluginExclusion> = emptyList(),
    override val policy: String? = null,
    override val policyPluginsDir: Path? = null,
    override val cacheDir: Path? = null,
    override val debugHttpCalls: Boolean = false,
    override val gradleCurrentVersionUrl: String = Configuration.DEFAULT_GRADLE_VERSION_URL,
    override val onlyCheckStaticVersions: Boolean = true,
) : Configuration

/**
 * Library exclusion info
 *
 * @property group The group of the library to exclude.
 * @property name The name of the library to exclude. If null, all libraries in the group are excluded.
 */
public data class LibraryExclusion(
    val group: String,
    val name: String? = null,
) : Serializable {
    private companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Wrapper for plugin id exclusion.
 */
public data class PluginExclusion(val id: String)

internal fun Configuration.isExcluded(dependencyKey: String, dependency: Dependency): Boolean {
    if (dependencyKey in excludedKeys) return true
    return when (dependency) {
        is Dependency.Library -> excludedLibraries.any { excluded ->
            if (excluded.name == null) {
                dependency.group == excluded.group
            } else {
                dependency.group == excluded.group && dependency.name == excluded.name
            }
        }

        is Dependency.Plugin -> excludedPlugins.any { it.id == dependency.id }
    }
}