/*
 * MIT License
 *
 * Copyright (c) 2026 Deezer
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
 *
 */

package com.deezer.caupain.config

import com.deezer.caupain.config.model.GradleWrapperProperties
import com.deezer.caupain.config.serialization.decodeFromProperties
import com.deezer.caupain.model.Configuration
import com.deezer.caupain.model.GradleConfiguration
import com.deezer.caupain.model.gradle.GradleStabilityLevel
import com.deezer.caupain.policies.StabilityLevelPolicy
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import com.deezer.caupain.config.model.Configuration as ParsedConfiguration

/**
 * Values provided by the user at invocation time (CLI options, action inputs...). They take
 * precedence over the values of the parsed configuration file.
 *
 * Collections are considered unset when empty, nullable values when null, and booleans when false.
 */
@Suppress("LongParameterList") // Reflects the available overrides
class ConfigurationOverrides(
    val versionCatalogPaths: List<Path> = emptyList(),
    val excludedKeys: Set<String> = emptySet(),
    val policies: List<String>? = null,
    val policyPluginsDir: Path? = null,
    val cacheDir: Path? = null,
    val disableCache: Boolean = false,
    val cleanCache: Boolean = false,
    val debugHttpCalls: Boolean = false,
    val gradleStabilityLevel: GradleStabilityLevel? = null,
    val searchReleaseNote: Boolean? = null,
    val githubToken: String? = null,
    val verifyExistence: Boolean = false,
)

/**
 * Merges the configuration file values with the values provided at invocation time, and with
 * the default values.
 */
object ConfigurationResolver {

    val DEFAULT_GRADLE_PROPERTIES_PATH: Path =
        "gradle/wrapper/gradle-wrapper.properties".toPath()

    /**
     * Creates the core configuration. Precedence is: [overrides], then [parsedConfiguration], then
     * default values.
     *
     * @param defaultCacheDir Provides the cache dir to use if none is specified
     * @param defaultGithubToken Provides the GitHub token to use if none is specified
     */
    @Suppress(
        "CyclomaticComplexMethod", // Just a lot of parameters and elvis operators
        "NullableBooleanCheck", // Used for readibility
        "UseOrEmpty", // Used for readibility
    )
    fun resolveConfiguration(
        parsedConfiguration: ParsedConfiguration?,
        overrides: ConfigurationOverrides = ConfigurationOverrides(),
        defaultCacheDir: () -> Path? = { null },
        defaultGithubToken: () -> String? = { null },
    ): Configuration {
        return Configuration(
            repositories = parsedConfiguration?.repositories?.map { it.toModel() }
                ?: Configuration.DEFAULT_REPOSITORIES,
            pluginRepositories = parsedConfiguration?.pluginRepositories?.map { it.toModel() }
                ?: Configuration.DEFAULT_PLUGIN_REPOSITORIES,
            versionCatalogPaths = overrides.versionCatalogPaths.takeUnless { it.isEmpty() }
                ?: parsedConfiguration?.versionCatalogPaths
                ?: parsedConfiguration?.versionCatalogPath?.let(::listOf)
                ?: listOf(Configuration.DEFAULT_CATALOG_PATH),
            excludedKeys = overrides.excludedKeys.takeUnless { it.isEmpty() }
                ?: parsedConfiguration?.excludedKeys
                ?: emptySet(),
            excludedLibraries = parsedConfiguration?.excludedLibraries.orEmpty(),
            excludedPlugins = parsedConfiguration?.excludedPlugins.orEmpty(),
            includedKeys = parsedConfiguration?.includedKeys.orEmpty(),
            includedLibraries = parsedConfiguration?.includedLibraries.orEmpty(),
            includedPlugins = parsedConfiguration?.includedPlugins.orEmpty(),
            filters = parsedConfiguration?.filters.orEmpty(),
            policies = overrides.policies
                ?.map { it.trim() }
                ?: parsedConfiguration?.policies
                ?: parsedConfiguration?.policy?.let(::listOf)
                ?: listOf(StabilityLevelPolicy.name),
            policyPluginsDir = overrides.policyPluginsDir
                ?: parsedConfiguration?.policyPluginDir,
            cacheDir = if (overrides.disableCache) {
                null
            } else {
                overrides.cacheDir
                    ?: parsedConfiguration?.cacheDir
                    ?: defaultCacheDir()
            },
            cleanCache = overrides.cleanCache,
            debugHttpCalls = overrides.debugHttpCalls,
            onlyCheckStaticVersions = parsedConfiguration?.onlyCheckStaticVersions != false,
            gradleStabilityLevel = overrides.gradleStabilityLevel
                ?: parsedConfiguration?.gradleStabilityLevel
                ?: GradleStabilityLevel.STABLE,
            checkIgnored = parsedConfiguration?.checkIgnored == true,
            searchReleaseNote = overrides.searchReleaseNote
                ?: parsedConfiguration?.searchReleaseNote
                ?: false,
            githubToken = overrides.githubToken
                ?: parsedConfiguration?.githubToken
                ?: defaultGithubToken(),
            verifyExistence = overrides.verifyExistence
                    || parsedConfiguration?.verifyExistence == true
        )
    }

    /**
     * Resolves the Gradle wrapper properties path to use. Precedence is: [override], then
     * [parsedConfiguration], then the default path.
     */
    fun resolveGradleWrapperPropertiesPath(
        parsedConfiguration: ParsedConfiguration?,
        override: Path? = null,
    ): Path = override
        ?: parsedConfiguration?.gradleWrapperPropertiesPath
        ?: DEFAULT_GRADLE_PROPERTIES_PATH

    /**
     * Loads the Gradle configuration from the properties file at [path], or null if the file does
     * not exist or does not contain a Gradle version. This does blocking IO.
     */
    fun loadGradleConfiguration(fileSystem: FileSystem, path: Path): GradleConfiguration? {
        if (!fileSystem.exists(path)) return null
        val wrapperProperties = decodeFromProperties<GradleWrapperProperties>(
            fileSystem = fileSystem,
            path = path
        )
        val currentVersion = wrapperProperties.gradleVersion ?: return null
        return GradleConfiguration(
            version = currentVersion,
            needsChecksum = wrapperProperties.distributionSha256Sum != null,
        )
    }
}
