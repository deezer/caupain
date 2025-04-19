package com.deezer.dependencies.plugin

import com.deezer.dependencies.model.Repository
import org.gradle.api.Plugin
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.initialization.Settings
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register

/**
 * Plugin to check for dependency updates.
 */
@Suppress("UnstableApiUsage")
open class DependencyUpdatePlugin : Plugin<Settings> {

    override fun apply(target: Settings) {
        val ext = target.extensions.create<DependenciesUpdateExtension>("dependencyUpdates")
        target.gradle.projectsEvaluated {
            rootProject.tasks.register<DependenciesUpdateTask>("checkDependencyUpdates") {
                group = "verification"
                description = "Check for dependency updates"
                versionCatalogFile.convention(
                    ext
                        .versionCatalogFile
                        .convention(
                            rootProject
                                .layout
                                .projectDirectory
                                .file("gradle/libs.versions.toml"))
                )
                excludedKeys.convention(ext.excludedKeys)
                excludedLibraries.convention(ext.excludedLibraries)
                excludedPluginIds.convention(ext.excludedPluginIds)
                outputFile.convention(
                    ext
                        .outputFile
                        .convention(
                            rootProject
                                .layout
                                .buildDirectory.file("reports/dependency-updates.html")
                        )
                )
                outputToConsole.convention(ext.outputToConsole)
                outputToFile.convention(ext.outputToFile)
                this.repositories.convention(target.dependencyResolutionManagement.repositories.toRepositories())
                this.pluginRepositories.convention(target.pluginManagement.repositories.toRepositories())
                useCache.convention(ext.useCache)
            }
        }
    }

    private fun RepositoryHandler.toRepositories(): List<Repository> {
        return mapNotNull { repository ->
            if (repository is MavenArtifactRepository && repository.url.scheme in ACCEPTED_SCHEMES) {
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

    companion object {
        private val ACCEPTED_SCHEMES = setOf("http", "https")
    }
}