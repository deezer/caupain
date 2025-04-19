package com.deezer.dependencies.model

/**
 * UpdateInfo is a data class that holds information about a dependency update.
 *
 * @property dependency The key of the dependency in the version catalog.
 * @property dependencyId The ID of the dependency.
 * @property name The detailed name of the dependency
 * @property url The URL of the dependency.
 * @property currentVersion The current version of the dependency.
 * @property updatedVersion The updated version of the dependency.
 */
public data class UpdateInfo(
    val dependency: String,
    val dependencyId: String,
    val name: String? = null,
    val url: String? = null,
    val currentVersion: String,
    val updatedVersion: String
) {
    public enum class Type(public val title: String) {
        LIBRARY("Libraries"), PLUGIN("Plugins")
    }
}
