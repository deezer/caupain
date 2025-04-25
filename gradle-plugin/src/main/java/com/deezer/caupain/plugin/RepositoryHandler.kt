@file:Suppress("UnstableApiUsage")

package com.deezer.caupain.plugin

import com.deezer.caupain.model.Repository
import org.gradle.api.Action
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.internal.GradleInternal
import org.gradle.api.invocation.Gradle
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.kotlin.dsl.listProperty
import javax.inject.Inject

/**
 * Repository handler for easy configuration
 */
abstract class RepositoryHandler @Inject constructor(
    objects: ObjectFactory,
    gradle: Gradle
) {

    /**
     * Libraries repositories to check for updates.
     */
    @get:Input
    val libraries: ListProperty<Repository> = objects.listProperty<Repository>().convention(
        gradle.dependenciesRepositoryHandler.toRepositories()
    )

    /**
     * Plugin repositories
     */
    @get:Input
    val plugins: ListProperty<Repository> = objects.listProperty<Repository>().convention(
        gradle.pluginsRepositoryHandler.toRepositories()
    )

    fun libraries(action: Action<RepositoryCategoryHandler>) {
        action.execute(RepositoryCategoryHandler(libraries))
    }

    fun plugins(action: Action<RepositoryCategoryHandler>) {
        action.execute(RepositoryCategoryHandler(plugins))
    }
}

private val Gradle.dependenciesRepositoryHandler: RepositoryHandler
    get() = (this as GradleInternal).settings.dependencyResolutionManagement.repositories

private val Gradle.pluginsRepositoryHandler: RepositoryHandler
    get() = (this as GradleInternal).settings.pluginManagement.repositories

private val ACCEPTED_SCHEMES = setOf("http", "https")

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

class RepositoryCategoryHandler(private val listProperty: ListProperty<Repository>) {

    fun repository(repository: Repository) {
        listProperty.add(repository)
    }

    /**
     * Adds a repository
     */
    fun repository(url: String) {
        listProperty.add(Repository(url))
    }

    /**
     * Adds a repository with authentication
     */
    fun repository(url: String, user: String, password: String) {
        listProperty.add(Repository(url, user, password))
    }
}