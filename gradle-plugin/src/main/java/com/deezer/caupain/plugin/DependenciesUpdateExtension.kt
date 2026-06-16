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

package com.deezer.caupain.plugin

import com.deezer.caupain.model.Filter
import com.deezer.caupain.model.GradleDependencyVersion
import com.deezer.caupain.model.LibraryExclusion
import com.deezer.caupain.model.LibraryInclusion
import com.deezer.caupain.model.gradle.GradleStabilityLevel
import com.deezer.caupain.plugin.internal.listProperty
import com.deezer.caupain.plugin.internal.property
import com.deezer.caupain.plugin.internal.setProperty
import org.gradle.api.Action
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Nested
import javax.inject.Inject

/**
 * Configuration for the Dependencies Update plugin.
 */
@Suppress("TooManyFunctions") // Normal for an extension
abstract class DependenciesUpdateExtension @Inject internal constructor(objects: ObjectFactory) {

    @get:Nested
    abstract val repositories: RepositoryHandler

    /**
     * The path to the version catalog file (default is gradle/libs.versions.toml").
     */
    val versionCatalogFile: RegularFileProperty = objects.fileProperty()

    /**
     * The path to the version catalog files (default uses a single gradle/libs.versions.toml").
     */
    val versionCatalogFiles: ConfigurableFileCollection = objects.fileCollection()

    /**
     * The set of keys in the version catalog for which updates should be ignored. Default is empty.
     */
    val excludedKeys: SetProperty<String> = objects.setProperty<String>().convention(emptySet())

    /**
     * The list of libraries to exclude from the update check. Default is empty.
     */
    val excludedLibraries: ListProperty<LibraryExclusion> =
        objects.listProperty<LibraryExclusion>().convention(emptyList())

    /**
     * The set of plugin IDs to exclude from the update check. Default is empty.
     */
    val excludedPluginIds: SetProperty<String> =
        objects.setProperty<String>().convention(emptySet())

    /**
     * The set of keys in the version catalog for which updates should be selected. Default is empty.
     */
    val includedKeys: SetProperty<String> = objects.setProperty<String>().convention(emptySet())

    /**
     * The list of libraries to include in the update check. Default is empty.
     */
    val includedLibraries: ListProperty<LibraryInclusion> =
        objects.listProperty<LibraryInclusion>().convention(emptyList())

    /**
     * The set of plugin IDs to include in the update check. Default is empty.
     */
    val includedPluginIds: SetProperty<String> =
        objects.setProperty<String>().convention(emptySet())

    /**
     * The list of filters to apply to the update check. Default is empty.
     */
    val filters: ListProperty<Filter> = objects.listProperty<Filter>().convention(emptyList())

    @get:Nested
    abstract val outputsHandler: OutputsHandler

    /**
     * Whether or not to show a version references block in the output. Default is false.
     */
    val showVersionReferences: Property<Boolean> = objects.property<Boolean>().convention(false)

    /**
     * Whether to use an HTTP cache for the update check. Default is true.
     */
    val useCache: Property<Boolean> = objects.property<Boolean>().convention(true)

    /**
     * Whether to check updates only for static versions (e.g., 1.0.0 or 1.0.0-SNAPSHOT) and not for
     * dynamic versions (e.g., 1.+). Default is true.
     */
    val onlyCheckStaticVersions: Property<Boolean> = objects.property<Boolean>().convention(true)

    /**
     * The desired Gradle stability level to use for the update check.
     */
    val gradleStabilityLevel: Property<GradleStabilityLevel> = objects
        .property<GradleStabilityLevel>()
        .convention(GradleStabilityLevel.STABLE)

    /**
     * Whether to check ignored dependencies. If true, it will check for updates on ignored
     */
    val checkIgnored: Property<Boolean> = objects.property<Boolean>().convention(false)

    /**
     * The GitHub token to use for API requests, if any.
     */
    val githubToken: Property<String> = objects.property<String>()

    /**
     * Whether or not to try to find the release note URL for the updated dependencies.
     * This only works if the source project for the dependency is hosted on GitHub.
     * This is true by default if a GitHub token is provided.
     *
     * @see githubToken
     */
    val searchReleaseNote: Property<Boolean> = objects.property<Boolean>().convention(false)

    /**
     * Whether to verify that .pom files exist in the repository before accepting a version as valid.
     * When enabled, only versions with available POM metadata will be considered for updates.
     * This helps ensure that suggested versions are actually downloadable. Default is false.
     *
     * **WARNING**: This option can increase total build time significantly due to the extra HTTP requests.
     */
    val verifyExistence: Property<Boolean> = objects.property<Boolean>().convention(false)

    /**
     * Whether to skip checking for updates to the Caupain Gradle plugin itself.
     * Default is false.
     */
    val doNotCheckSelfUpdates: Property<Boolean> = objects.property<Boolean>().convention(false)

    /**
     * Configure repositories
     */
    fun repositories(action: Action<RepositoryHandler>) {
        action.execute(repositories)
    }

    /**
     * Excludes keys from the update check.
     */
    fun excludeKeys(vararg keys: String) {
        excludedKeys.addAll(keys.asIterable())
    }

    /**
     * Excludes pluginIds from the update check.
     */
    fun excludePluginIds(vararg pluginIds: String) {
        excludedPluginIds.addAll(pluginIds.asIterable())
    }

    /**
     * Excludes a library from the update check.
     *
     * @param group The group ID of the library.
     * @param name The artifact ID of the library. If null, all libraries in the group are excluded.
     */
    fun excludeLibrary(group: String, name: String? = null) {
        excludedLibraries.add(LibraryExclusion(group, name))
    }

    /**
     * Excludes libraries from the update check.
     */
    fun excludeLibraries(vararg libraries: LibraryExclusion) {
        excludedLibraries.addAll(libraries.asIterable())
    }

    /**
     * Includes keys in the update check.
     */
    fun includeKeys(vararg keys: String) {
        includedKeys.addAll(keys.asIterable())
    }

    /**
     * Includes pluginIds in the update check.
     */
    fun includePluginIds(vararg pluginIds: String) {
        includedPluginIds.addAll(pluginIds.asIterable())
    }

    /**
     * Includes a library in the update check.
     *
     * @param group The group ID of the library.
     * @param name The artifact ID of the library. If null, all libraries in the group are selected.
     */
    fun includeLibrary(group: String, name: String? = null) {
        includedLibraries.add(LibraryInclusion(group, name))
    }

    /**
     * Includes libraries in the update check.
     */
    fun includeLibraries(vararg libraries: LibraryInclusion) {
        includedLibraries.addAll(libraries.asIterable())
    }

    /**
     * Add a filter on a library to the update check.
     */
    fun addFilter(group: String, name: String? = null, versionFilter: String) {
        filters.add(Filter.LibraryFilter(group, name, GradleDependencyVersion(versionFilter)))
    }

    /**
     * Add a filter on a plugin to the update check.
     */
    fun addFilter(pluginId: String, versionFilter: String) {
        filters.add(Filter.PluginFilter(pluginId, GradleDependencyVersion(versionFilter)))
    }

    /**
     * Configures the outputs
     */
    fun outputs(action: Action<OutputsHandler>) {
        action.execute(outputsHandler)
    }
}
