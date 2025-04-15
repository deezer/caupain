package com.deezer.dependencies.model

import com.deezer.dependencies.Serializable
import okio.Path
import okio.Path.Companion.toPath

public data class Configuration(
    val repositories: List<Repository> = listOf(
        DefaultRepositories.mavenCentral,
        DefaultRepositories.google,
    ),
    val pluginRepositories: List<Repository> = listOf(
        DefaultRepositories.gradlePlugins,
        DefaultRepositories.mavenCentral,
        DefaultRepositories.google,
    ),
    val versionCatalogPath: Path = "gradle/libs.versions.toml".toPath(),
    val excludedKeys: Set<String> = emptySet(),
    val excludedLibraries: List<LibraryExclusion> = emptyList(),
    val excludedPlugins: List<PluginExclusion> = emptyList(),
    val policy: String? = null,
    val policyPluginDir: Path? = null,
) : Serializable

public data class LibraryExclusion(
    val group: String,
    val name: String? = null,
) : Serializable

public data class PluginExclusion(val id: String)

internal fun Configuration.isExcluded(dependencyKey: String, dependency: Dependency): Boolean {
    if (dependencyKey in excludedKeys) return true
    return when (dependency) {
        is Dependency.Library -> excludedLibraries.any { excluded ->
            if (excluded.name == null) {
                dependency.group == excluded.group
            } else {
                dependency.group == excluded.group && dependency.name == excluded.name
            }
        }

        is Dependency.Plugin -> excludedPlugins.any { it.id == dependency.id }
    }
}