package com.deezer.dependencies.plugin

import com.deezer.dependencies.model.Repository
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.register

@Suppress("UnstableApiUsage")
open class DependencyUpdatePlugin : Plugin<Project> {

    override fun apply(target: Project) {
        require(target.rootProject == target) {
            "Plugin must be applied to the root project"
        }
        val ext = target.extensions.create<DependenciesUpdateExtension>("dependencyUpdates")
        val repositories = target.objects.listProperty<Repository>()
        val pluginRepositories = target.objects.listProperty<Repository>()
        target.tasks.register<DependenciesUpdateTask>("checkDependencyUpdates") {
            group = "verification"
            description = "Check for dependency updates"
            versionCatalogFile.set(ext.versionCatalogFile)
            excludedKeys.set(ext.excludedKeys)
            excludedLibraries.set(ext.excludedLibraries)
            excludedPluginIds.set(ext.excludedPluginIds)
            outputFile.set(ext.outputFile)
            outputToConsole.set(ext.outputToConsole)
            outputToFile.set(ext.outputToFile)
            this.repositories.set(repositories)
            this.pluginRepositories.set(pluginRepositories)
        }
        target.gradle.settingsEvaluated {
            repositories.set(dependencyResolutionManagement.repositories.toRepositories())
            pluginRepositories.set(pluginManagement.repositories.toRepositories())
        }
    }

    private fun RepositoryHandler.toRepositories(): List<Repository> {
        return mapNotNull { repository ->
            if (repository is MavenArtifactRepository) {
                Repository(
                    url = repository.url.toString(),
                    user = repository.credentials.username,
                    password = repository.credentials.password
                )
            } else {
                null
            }
        }
    }
}