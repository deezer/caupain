package com.deezer.caupain.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register

/**
 * Plugin to check for dependency updates.
 */
open class DependencyUpdatePlugin : Plugin<Project> {

    override fun apply(target: Project) {
        val ext = target.extensions.create<DependenciesUpdateExtension>("caupain")
        require(target == target.rootProject) {
            "Plugin must be applied to the root project"
        }
        target.tasks.register<DependenciesUpdateTask>("checkDependencyUpdates") {
            group = "verification"
            description = "Check for dependency updates"
            versionCatalogFile.convention(
                ext
                    .versionCatalogFile
                    .convention(
                        target
                            .layout
                            .projectDirectory
                            .file("gradle/libs.versions.toml")
                    )
            )
            excludedKeys.convention(ext.excludedKeys)
            excludedLibraries.convention(ext.excludedLibraries)
            excludedPluginIds.convention(ext.excludedPluginIds)
            formatterOutputs.convention(ext.outputsHandler.outputs)
            repositories.convention(ext.repositories.libraries)
            pluginRepositories.convention(ext.repositories.plugins)
            useCache.convention(ext.useCache)
            onlyCheckStaticVersions.convention(ext.onlyCheckStaticVersions)
        }
    }
}