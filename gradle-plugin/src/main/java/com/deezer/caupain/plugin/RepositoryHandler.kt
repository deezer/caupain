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

import com.deezer.caupain.Serializable
import com.deezer.caupain.model.ComponentFilterBuilder
import com.deezer.caupain.model.DefaultRepositories
import com.deezer.caupain.model.HeaderCredentials
import com.deezer.caupain.model.PasswordCredentials
import com.deezer.caupain.model.Repository
import com.deezer.caupain.model.buildComponentFilter
import com.deezer.caupain.model.withComponentFilter
import com.deezer.caupain.plugin.internal.combine
import com.deezer.caupain.plugin.internal.listProperty
import com.deezer.caupain.plugin.internal.property
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import javax.inject.Inject

/**
 * Repository handler for easy configuration
 */
open class RepositoryHandler @Inject constructor(
    private val objects: ObjectFactory,
    private val providers: ProviderFactory,
) {
    internal val libraryRepositoryInfos: ListProperty<RepositoryInfo> = objects.listProperty()

    internal val pluginRepositoryInfos: ListProperty<RepositoryInfo> = objects.listProperty()

    internal val isCompatibleWithConfigCache: Provider<Boolean> = combine(
        libraryRepositoryInfos.map { infos ->
            infos.all { it.isCompatibleWithConfigCache }
        },
        pluginRepositoryInfos.map { infos ->
            infos.all { it.isCompatibleWithConfigCache }
        },
    ) { areLibraryRepositoriesCompatible, arePluginRepositoriesCompatible ->
        areLibraryRepositoriesCompatible && arePluginRepositoriesCompatible
    }


    /**
     * Libraries repositories to check for updates.
     */
    val libraries: Provider<List<Repository>> = libraryRepositoryInfos.map { infos ->
        infos.map { it.repository }
    }

    /**
     * Plugin repositories
     */
    val plugins: Provider<List<Repository>> = pluginRepositoryInfos.map { infos ->
        infos.map { it.repository }
    }

    fun libraries(action: Action<RepositoryCategoryHandler>) {
        action.execute(
            RepositoryCategoryHandler(
                objects = objects,
                providers = providers,
                listProperty = libraryRepositoryInfos,
            )
        )
    }

    fun plugins(action: Action<RepositoryCategoryHandler>) {
        action.execute(
            RepositoryCategoryHandler(
                objects = objects,
                providers = providers,
                listProperty = pluginRepositoryInfos,
            )
        )
    }
}

class RepositoryCategoryHandler internal constructor(
    private val objects: ObjectFactory,
    private val providers: ProviderFactory,
    private val listProperty: ListProperty<RepositoryInfo>,
) {

    fun repository(repository: Repository) {
        listProperty.add(
            RepositoryInfo(
                repository = repository,
                isCompatibleWithConfigCache = repository.isCompatibleWithConfigCache,
            )
        )
    }

    fun repository(
        repository: Repository,
        configureComponentFilter: Action<ComponentFilterBuilder>
    ) {
        listProperty.add(
            RepositoryInfo(
                repository = repository
                    .withComponentFilter { configureComponentFilter.execute(this) },
                isCompatibleWithConfigCache = repository.isCompatibleWithConfigCache,
            )
        )
    }

    fun repository(url: String) {
        listProperty.add(
            RepositoryInfo(
                repository = Repository(url),
                isCompatibleWithConfigCache = true,
            )
        )
    }

    fun repository(url: String, configure: Action<RepositoryConfigurationHandler>) {
        listProperty.add(
            RepositoryConfigurationHandler(objects, providers)
                .apply { configure.execute(this) }
                .toRepository(url)
        )
    }

    fun repository(url: String, user: String, password: String) {
        listProperty.add(
            RepositoryInfo(
                repository = Repository(url, user, password),
                isCompatibleWithConfigCache = true,
            )
        )
    }

    fun repository(
        url: String,
        user: String,
        password: String,
        configureComponentFilter: Action<ComponentFilterBuilder>
    ) {
        listProperty.add(
            RepositoryInfo(
                repository = Repository(
                    url = url,
                    user = user,
                    password = password,
                    componentFilter = buildComponentFilter {
                        configureComponentFilter.execute(this)
                    }
                ),
                isCompatibleWithConfigCache = true,
            )
        )
    }

    companion object {
        private val Repository.isCompatibleWithConfigCache: Boolean
            get() = this in DefaultRepositories.all
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

    internal fun toRepository(url: String): Provider<RepositoryInfo> {
        val credentialsProvider = credentialsHandler?.toCredentials()
            ?: headerCredentialsHandler?.toCredentials()
        return credentialsProvider
            ?.map { credentials ->
                RepositoryInfo(
                    repository = Repository(
                        url = url,
                        credentials = credentials,
                        componentFilter = filterBuilder.build()
                    ),
                    isCompatibleWithConfigCache = false,
                )
            }
            ?: providers.provider {
                RepositoryInfo(
                    repository = Repository(url, filterBuilder.build()),
                    isCompatibleWithConfigCache = true,
                )
            }
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

internal class RepositoryInfo(
    val repository: Repository,
    val isCompatibleWithConfigCache: Boolean,
) : Serializable {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RepositoryInfo

        return repository == other.repository
    }

    override fun hashCode(): Int {
        return repository.hashCode()
    }

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
