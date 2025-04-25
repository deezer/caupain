package com.deezer.caupain.plugin

import com.deezer.caupain.model.LibraryExclusion
import org.gradle.api.Action
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
abstract class DependenciesUpdateExtension @Inject constructor(objects: ObjectFactory) {

    @get:Nested
    abstract val repositories: RepositoryHandler

    /**
     * The path to the version catalog file (default is gradle/libs.versions.toml").
     */
    val versionCatalogFile: RegularFileProperty = objects.fileProperty()

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

    /**
     * The path to the output file for the HTML report (default is "build/reports/dependency-updates.html").
     */
    val outputFile: RegularFileProperty = objects.fileProperty()

    /**
     * Whether to output the results to the console. Default is true.
     */
    val outputToConsole: Property<Boolean> = objects.property<Boolean>().convention(true)

    /**
     * Whether to output the results to a file. Default is true.
     */
    val outputToFile: Property<Boolean> = objects.property<Boolean>().convention(true)

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
    @Suppress("unused")
    fun excludeLibrary(group: String, name: String? = null) {
        excludedLibraries.add(LibraryExclusion(group, name))
    }

    /**
     * Excludes libraries from the update check.
     */
    fun excludeLibraries(vararg libraries: LibraryExclusion) {
        excludedLibraries.addAll(libraries.asIterable())
    }
}