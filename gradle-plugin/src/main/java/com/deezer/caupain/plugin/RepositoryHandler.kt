package com.deezer.caupain.plugin

import com.deezer.caupain.model.Repository
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Input
import org.gradle.kotlin.dsl.listProperty
import javax.inject.Inject

/**
 * Repository handler for easy configuration
 */
abstract class RepositoryHandler @Inject constructor(objects: ObjectFactory) {

    /**
     * Libraries repositories to check for updates.
     */
    @get:Input
    val libraries = objects.listProperty<Repository>()

    /**
     * Plugin repositories
     */
    @get:Input
    val plugins = objects.listProperty<Repository>()

    /**
     * Adds a repository
     */
    fun repository(url: String) {
        libraries.add(Repository(url))
    }

    /**
     * Adds a repository with authentication
     */
    fun repository(url: String, user: String, password: String) {
        libraries.add(Repository(url, user, password))
    }

    /**
     * Adds a plugin repository
     */
    fun pluginRepository(url: String) {
        plugins.add(Repository(url))
    }

    /**
     * Adds a plugin repository with authentication
     */
    fun pluginRepository(url: String, user: String, password: String) {
        plugins.add(Repository(url, user, password))
    }
}