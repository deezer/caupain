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

@file:Suppress("UnstableApiUsage")

package com.deezer.caupain.plugin

import com.deezer.caupain.model.ComponentFilter
import com.deezer.caupain.model.Dependency
import com.deezer.caupain.model.Dependency.Library
import com.deezer.caupain.model.Dependency.Plugin
import com.deezer.caupain.model.Repository
import org.gradle.api.Action
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.artifacts.repositories.ArtifactResolutionDetails
import org.gradle.api.internal.artifacts.repositories.ContentFilteringRepository
import org.gradle.api.invocation.Gradle
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.internal.artifacts.repositories.AuthenticationSupportedInternal
import org.gradle.kotlin.dsl.listProperty
import java.util.Optional
import javax.inject.Inject
import kotlin.jvm.optionals.getOrNull

/**
 * Repository handler for easy configuration
 */
@Suppress("UnnecessaryAbstractClass") // Needed by Gradle
abstract class RepositoryHandler @Inject constructor(
    objects: ObjectFactory,
    gradle: Gradle
) {

    /**
     * Libraries repositories to check for updates.
     */
    @get:Input
    val libraries: ListProperty<Repository> = objects.listProperty<Repository>().convention(
        gradle.dependenciesRepositoryHandler.toRepositories(objects)
    )

    /**
     * Plugin repositories
     */
    @get:Input
    val plugins: ListProperty<Repository> = objects.listProperty<Repository>().convention(
        gradle.pluginsRepositoryHandler.toRepositories(objects)
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

private fun RepositoryHandler.toRepositories(objects: ObjectFactory): Provider<List<Repository>> {
    val repositoriesProvider = objects.listProperty<Repository>()
    for (repository in this) {
        if (repository is MavenArtifactRepository && repository.url.scheme in ACCEPTED_SCHEMES) {
            val url = repository.url.toString()
            val credentials =
                (repository as? AuthenticationSupportedInternal)?.configuredCredentials
            if (credentials == null) {
                repositoriesProvider.add(Repository(url = url))
            } else {
                repositoriesProvider.add(
                    credentials
                        .map { Optional.of(it) }
                        .orElse(Optional.empty())
                        .map { optionalCredentials ->
                            val passwordCredentials =
                                optionalCredentials.getOrNull() as? PasswordCredentials
                            Repository(
                                url = url,
                                user = passwordCredentials?.username,
                                password = passwordCredentials?.password
                            )
                        }
                )
            }
        }
    }
    return repositoriesProvider
}

class ComponentFilterAdapter(private val delegate: Action<in ArtifactResolutionDetails>) :
    ComponentFilter {

    override fun accepts(dependency: Dependency): Boolean {
        if (dependency.group == null || dependency.name == null) return false
        return ArtifactResolutionDetailsAdapter(dependency)
            .apply { delegate.execute(this) }
            .isFound
    }

    private class ModuleIdentifierAdapter(private val dependency: Dependency) : ModuleIdentifier {
        override fun getGroup(): String = dependency.group!!

        override fun getName(): String = dependency.name!!
    }

    private class ModuleComponentIdentifierAdapter(private val dependency: Dependency) :
        ModuleComponentIdentifier {
        private val identifier = ModuleIdentifierAdapter(dependency)

        override fun getDisplayName(): String = dependency.moduleId

        override fun getGroup(): String = identifier.group

        override fun getModule(): String = identifier.name

        override fun getVersion(): String = dependency.version!!.toString()

        override fun getModuleIdentifier(): ModuleIdentifier = identifier
    }

    private class ArtifactResolutionDetailsAdapter(
        private val dependency: Dependency
    ) : ArtifactResolutionDetails {

        var isFound = true
            private set

        override fun getModuleId(): ModuleIdentifier = ModuleIdentifierAdapter(dependency)

        override fun getComponentId(): ModuleComponentIdentifier? =
            if (dependency.version == null) {
                null
            } else {
                ModuleComponentIdentifierAdapter(dependency)
            }

        override fun isVersionListing(): Boolean = dependency.version != null

        override fun notFound() {
            isFound = false
        }
    }

    companion object {
        private val Dependency.group: String?
            get() = when (this) {
                is Library -> group
                is Plugin -> id
            }

        private val Dependency.name: String?
            get() = when (this) {
                is Library -> name
                is Plugin -> "$id.gradle.plugin"
            }
    }
}

class RepositoryCategoryHandler internal constructor(private val listProperty: ListProperty<Repository>) {

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