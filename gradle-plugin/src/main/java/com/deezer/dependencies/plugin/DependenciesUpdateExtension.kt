package com.deezer.dependencies.plugin

import com.deezer.dependencies.model.LibraryExclusion
import org.gradle.api.model.ObjectFactory
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.setProperty
import javax.inject.Inject

/**
 * Configuration for the Dependencies Update plugin.
 */
open class DependenciesUpdateExtension @Inject constructor(objects: ObjectFactory) {
    /**
     * The path to the version catalog file (default is gradle/libs.versions.toml").
     */
    val versionCatalogFile = objects.fileProperty()

    /**
     * The set of keys in the version catalog for which updates should be ignored. Default is empty.
     */
    val excludedKeys = objects.setProperty<String>().convention(emptySet())

    /**
     * The list of libraries to exclude from the update check. Defauklt is empty.
     */
    val excludedLibraries = objects.listProperty<LibraryExclusion>().convention(emptyList())

    /**
     * The set of plugin IDs to exclude from the update check. Default is empty.
     */
    val excludedPluginIds = objects.setProperty<String>().convention(emptySet())

    /**
     * The path to the output file for the HTML report (default is "build/reports/dependency-updates.html").
     */
    val outputFile = objects.fileProperty()

    /**
     * Whether to output the results to the console. Default is true.
     */
    val outputToConsole = objects.property<Boolean>().convention(true)

    /**
     * Whether to output the results to a file. Default is true.
     */
    val outputToFile = objects.property<Boolean>().convention(true)

    /**
     * Whether to use an HTTP cache for the update check. Default is true.
     */
    val useCache = objects.property<Boolean>().convention(true)

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
}