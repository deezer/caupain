package com.deezer.dependencies.plugin

import com.deezer.dependencies.model.LibraryExclusion
import org.gradle.api.model.ObjectFactory
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.setProperty
import javax.inject.Inject

open class DependenciesUpdateExtension @Inject constructor(objects: ObjectFactory) {
    val versionCatalogFile = objects.fileProperty()

    val excludedKeys = objects.setProperty<String>().convention(emptySet())

    val excludedLibraries = objects.listProperty<LibraryExclusion>().convention(emptyList())

    val excludedPluginIds = objects.setProperty<String>().convention(emptySet())

    val outputFile = objects.fileProperty()

    val outputToConsole = objects.property<Boolean>().convention(true)

    val outputToFile = objects.property<Boolean>().convention(true)

    @Suppress("unused")
    fun excludeLibrary(group: String, name: String? = null) {
        excludedLibraries.add(LibraryExclusion(group, name))
    }
}