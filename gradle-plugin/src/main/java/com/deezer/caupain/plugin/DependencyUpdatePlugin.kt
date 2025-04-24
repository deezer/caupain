package com.deezer.caupain.plugin

import com.deezer.caupain.model.Repository
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.internal.GradleInternal
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register

/**
 * Plugin to check for dependency updates.
 */
@Suppress("UnstableApiUsage")
open class DependencyUpdatePlugin : Plugin<Project> {

    override fun apply(target: Project) {
        val ext = target.extensions.create<DependenciesUpdateExtension>("caupain")
        require(target == target.rootProject) {
            "Plugin must be applied to the root project"
        }
        val settings = (target.gradle as GradleInternal).settings
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
            outputFile.convention(
                ext
                    .outputFile
                    .convention(
                        target
                            .layout
                            .buildDirectory.file("reports/dependency-updates.html")
                    )
            )
            outputToConsole.convention(ext.outputToConsole)
            outputToFile.convention(ext.outputToFile)
            this.repositories.convention(settings.dependencyResolutionManagement.repositories.toRepositories())
            this.pluginRepositories.convention(settings.pluginManagement.repositories.toRepositories())
            useCache.convention(ext.useCache)
            onlyCheckStaticVersions.convention(ext.onlyCheckStaticVersions)
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