@file:UseSerializers(
    RepositorySerializer::class,
    LibraryExclusionSerializer::class,
    PathSerializer::class,
    PluginExclusionSerializer::class
)

package com.deezer.dependencies.cli.model

import com.deezer.dependencies.cli.serialization.LibraryExclusionSerializer
import com.deezer.dependencies.cli.serialization.PathSerializer
import com.deezer.dependencies.cli.serialization.PluginExclusionSerializer
import com.deezer.dependencies.cli.serialization.RepositorySerializer
import com.deezer.dependencies.model.Configuration
import com.deezer.dependencies.model.LibraryExclusion
import com.deezer.dependencies.model.PluginExclusion
import com.deezer.dependencies.model.Repository
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import okio.Path

@Serializable
data class Configuration(
    val repositories: List<Repository>? = null,
    val pluginRepositories: List<Repository>? = null,
    val versionCatalogPath: Path? = null,
    val excludedKeys: Set<String>? = null,
    val excludedLibraries: List<LibraryExclusion>? = null,
    val excludedPlugins: List<PluginExclusion>? = null,
    val policy: String? = null,
    val policyPluginDir: Path? = null,
) {
    fun toConfiguration(baseConfiguration: Configuration): Configuration {
        return Configuration(
            repositories = repositories ?: baseConfiguration.repositories,
            pluginRepositories = pluginRepositories ?: baseConfiguration.pluginRepositories,
            versionCatalogPath = versionCatalogPath ?: baseConfiguration.versionCatalogPath,
            excludedKeys = excludedKeys ?: baseConfiguration.excludedKeys,
            excludedLibraries = excludedLibraries ?: baseConfiguration.excludedLibraries,
            excludedPlugins = excludedPlugins ?: baseConfiguration.excludedPlugins,
            policy = policy ?: baseConfiguration.policy,
            policyPluginDir = policyPluginDir ?: baseConfiguration.policyPluginDir,
        )
    }
}