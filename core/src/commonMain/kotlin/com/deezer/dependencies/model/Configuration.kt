package com.deezer.dependencies.model

import com.deezer.dependencies.Serializable
import okio.Path
import okio.Path.Companion.toPath

public interface Configuration : Serializable {
    public val repositories: List<Repository>
    public val pluginRepositories: List<Repository>
    public val versionCatalogPath: Path
    public val excludedKeys: Set<String>
    public val excludedLibraries: List<LibraryExclusion>
    public val excludedPlugins: List<PluginExclusion>
    public val policy: String?
    public val policyPluginDir: Path?
    public val cacheDir: Path?

    public companion object {
        private const val serialVersionUID = 1L
    }
}

@Suppress("LongParameterList")
public fun Configuration(
    repositories: List<Repository> = listOf(
        DefaultRepositories.mavenCentral,
        DefaultRepositories.google,
    ),
    pluginRepositories: List<Repository> = listOf(
        DefaultRepositories.gradlePlugins,
        DefaultRepositories.mavenCentral,
        DefaultRepositories.google,
    ),
    versionCatalogPath: Path = "gradle/libs.versions.toml".toPath(),
    excludedKeys: Set<String> = emptySet(),
    excludedLibraries: List<LibraryExclusion> = emptyList(),
    excludedPlugins: List<PluginExclusion> = emptyList(),
    policy: String? = null,
    policyPluginDir: Path? = null,
    cacheDir: Path? = null,
): Configuration = ConfigurationImpl(
    repositories = repositories,
    pluginRepositories = pluginRepositories,
    versionCatalogPath = versionCatalogPath,
    excludedKeys = excludedKeys,
    excludedLibraries = excludedLibraries,
    excludedPlugins = excludedPlugins,
    policy = policy,
    policyPluginDir = policyPluginDir,
    cacheDir = cacheDir
)

internal data class ConfigurationImpl(
    override val repositories: List<Repository> = listOf(
        DefaultRepositories.mavenCentral,
        DefaultRepositories.google,
    ),
    override val pluginRepositories: List<Repository> = listOf(
        DefaultRepositories.gradlePlugins,
        DefaultRepositories.mavenCentral,
        DefaultRepositories.google,
    ),
    override val versionCatalogPath: Path = "gradle/libs.versions.toml".toPath(),
    override val excludedKeys: Set<String> = emptySet(),
    override val excludedLibraries: List<LibraryExclusion> = emptyList(),
    override val excludedPlugins: List<PluginExclusion> = emptyList(),
    override val policy: String? = null,
    override val policyPluginDir: Path? = null,
    override val cacheDir: Path? = null,
) : Configuration

public data class LibraryExclusion(

    val group: String,
    val name: String? = null,
) : Serializable {
    public companion object {
        private const val serialVersionUID = 1L
    }
}

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