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

import com.deezer.caupain.model.ComponentFilterBuilder
import com.deezer.caupain.model.HeaderCredentials
import com.deezer.caupain.model.PasswordCredentials
import com.deezer.caupain.model.Repository
import com.deezer.caupain.model.buildComponentFilter
import com.deezer.caupain.model.withComponentFilter
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.property
import javax.inject.Inject

/**
 * Repository handler for easy configuration
 */
open class RepositoryHandler @Inject constructor(
    private val objects: ObjectFactory,
    private val providers: ProviderFactory,
) {

    /**
     * Libraries repositories to check for updates.
     */
    val libraries: ListProperty<Repository> = objects.listProperty()

    /**
     * Plugin repositories
     */
    val plugins: ListProperty<Repository> = objects.listProperty()

    fun libraries(action: Action<RepositoryCategoryHandler>) {
        action.execute(RepositoryCategoryHandler(objects, providers, libraries))
    }

    fun plugins(action: Action<RepositoryCategoryHandler>) {
        action.execute(RepositoryCategoryHandler(objects, providers, plugins))
    }

    internal fun setupConvention(
        libraryRepositories: Provider<out Iterable<Repository>>,
        pluginRepositories: Provider<out Iterable<Repository>>
    ) {
        libraries.convention(libraryRepositories)
        plugins.convention(pluginRepositories)
    }
}

class RepositoryCategoryHandler internal constructor(
    private val objects: ObjectFactory,
    private val providers: ProviderFactory,
    private val listProperty: ListProperty<Repository>
) {

    fun repository(repository: Repository) {
        listProperty.add(repository)
    }

    fun repository(
        repository: Repository,
        configureComponentFilter: Action<ComponentFilterBuilder>
    ) {
        listProperty.add(repository.withComponentFilter { configureComponentFilter.execute(this) })
    }

    fun repository(url: String) {
        listProperty.add(Repository(url))
    }

    fun repository(url: String, configure: Action<RepositoryConfigurationHandler>) {
        listProperty.add(
            RepositoryConfigurationHandler(objects, providers)
                .apply { configure.execute(this) }
                .toRepository(url)
        )
    }

    fun repository(url: String, user: String, password: String) {
        listProperty.add(Repository(url, user, password))
    }

    fun repository(
        url: String,
        user: String,
        password: String,
        configureComponentFilter: Action<ComponentFilterBuilder>
    ) {
        listProperty.add(
            Repository(
                url = url,
                user = user,
                password = password,
                componentFilter = buildComponentFilter {
                    configureComponentFilter.execute(this)
                }
            )
        )
    }
}

open class RepositoryConfigurationHandler internal constructor(
    private val objects: ObjectFactory,
    private val providers: ProviderFactory,
) {

    private val filterBuilder = ComponentFilterBuilder()

    private var credentialsHandler: CredentialsHandler? = null

    private var headerCredentialsHandler: HeaderCredentialsHandler? = null

    fun credentials(action: Action<CredentialsHandler>) {
        CredentialsHandler(objects)
            .apply { action.execute(this) }
            .also { credentialsHandler = it }
    }

    fun headerCredentials(action: Action<HeaderCredentialsHandler>) {
        HeaderCredentialsHandler(objects)
            .apply { action.execute(this) }
            .also { headerCredentialsHandler = it }
    }

    @JvmOverloads
    fun exclude(group: String, name: String? = null) {
        filterBuilder.exclude(group, name)
    }

    @JvmOverloads
    fun include(group: String, name: String? = null) {
        filterBuilder.include(group, name)
    }

    fun toRepository(url: String): Provider<Repository> {
        val credentialsProvider = credentialsHandler?.toCredentials()
            ?: headerCredentialsHandler?.toCredentials()
        return credentialsProvider
            ?.map { credentials ->
                Repository(
                    url = url,
                    credentials = credentials,
                    componentFilter = filterBuilder.build()
                )
            }
            ?: providers.provider { Repository(url, filterBuilder.build()) }
    }
}

open class CredentialsHandler internal constructor(objects: ObjectFactory) {
    val user = objects.property<String>()
    val password = objects.property<String>()

    internal fun toCredentials(): Provider<PasswordCredentials> = user.flatMap { user ->
        password.map { password ->
            PasswordCredentials(user, password)
        }
    }
}

open class HeaderCredentialsHandler internal constructor(objects: ObjectFactory) {
    val name = objects.property<String>()
    val value = objects.property<String>()

    internal fun toCredentials(): Provider<HeaderCredentials> = name.flatMap { name ->
        value.map { value ->
            HeaderCredentials(name, value)
        }
    }
}