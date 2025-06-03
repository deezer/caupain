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

import com.deezer.caupain.model.LibraryExclusion
import org.gradle.api.Action
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Nested
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.setProperty
import javax.inject.Inject

/**
 * Configuration for the Dependencies Update plugin.
 */
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
     * The list of libraries to exclude from the update check. Defauklt is empty.
     */
    val excludedLibraries: ListProperty<LibraryExclusion> =
        objects.listProperty<LibraryExclusion>().convention(emptyList())

    /**
     * The set of plugin IDs to exclude from the update check. Default is empty.
     */
    val excludedPluginIds: SetProperty<String> =
        objects.setProperty<String>().convention(emptySet())

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
     * Configures the outputs
     */
    fun outputs(action: Action<OutputsHandler>) {
        action.execute(outputsHandler)
    }
}