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
import com.deezer.caupain.model.Repository
import com.deezer.caupain.model.buildComponentFilter
import com.deezer.caupain.model.withComponentFilter
import org.gradle.api.Action
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider

/**
 * Repository handler for easy configuration
 */
@Suppress("UnnecessaryAbstractClass") // Needed by Gradle
abstract class RepositoryHandler {

    /**
     * Libraries repositories to check for updates.
     */
    abstract val libraries: ListProperty<Repository>

    /**
     * Plugin repositories
     */
    abstract val plugins: ListProperty<Repository>

    fun libraries(action: Action<RepositoryCategoryHandler>) {
        action.execute(RepositoryCategoryHandler(libraries))
    }

    fun plugins(action: Action<RepositoryCategoryHandler>) {
        action.execute(RepositoryCategoryHandler(plugins))
    }

    internal fun setupConvention(
        libraryRepositories: Provider<out Iterable<Repository>>,
        pluginRepositories: Provider<out Iterable<Repository>>
    ) {
        libraries.convention(libraryRepositories)
        plugins.convention(pluginRepositories)
    }
}

class RepositoryCategoryHandler internal constructor(private val listProperty: ListProperty<Repository>) {

    fun repository(repository: Repository) {
        listProperty.add(repository)
    }

    fun repository(
        repository: Repository,
        configureComponentFilter: Action<ComponentFilterBuilder>
    ) {
        listProperty.add(repository.withComponentFilter { configureComponentFilter.execute(this) })
    }

    /**
     * Adds a repository
     */
    fun repository(url: String) {
        listProperty.add(Repository(url))
    }

    fun repository(url: String, configureComponentFilter: Action<ComponentFilterBuilder>) {
        listProperty.add(
            Repository(
                url = url,
                componentFilter = buildComponentFilter {
                    configureComponentFilter.execute(this)
                }
            )
        )
    }

    /**
     * Adds a repository with authentication
     */
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