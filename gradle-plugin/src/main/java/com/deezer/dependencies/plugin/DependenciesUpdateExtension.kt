package com.deezer.dependencies.plugin

import com.deezer.dependencies.model.LibraryExclusion
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.setProperty
import javax.inject.Inject

open class DependenciesUpdateExtension @Inject constructor(
    layout: ProjectLayout,
    objects: ObjectFactory
) {
    val versionCatalogFile = objects
        .fileProperty()
        .convention(layout.projectDirectory.file("gradle/libs.versions.toml"))

    val excludedKeys = objects.setProperty<String>().convention(emptySet())

    val excludedLibraries = objects.listProperty<LibraryExclusion>().convention(emptyList())

    val excludedPluginIds = objects.setProperty<String>().convention(emptySet())

    val outputFile = objects
        .fileProperty()
        .convention(layout.buildDirectory.file("reports/dependency-updates.html"))

    val outputToConsole = objects.property<Boolean>().convention(true)

    val outputToFile = objects.property<Boolean>().convention(true)
}