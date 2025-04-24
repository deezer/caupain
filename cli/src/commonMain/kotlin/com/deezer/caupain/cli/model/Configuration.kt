package com.deezer.caupain.cli.model

import com.deezer.caupain.cli.serialization.ConfigurationSerializer
import com.deezer.caupain.model.LibraryExclusion
import com.deezer.caupain.model.PluginExclusion
import com.deezer.caupain.model.Repository
import kotlinx.serialization.Serializable
import okio.Path
import com.deezer.caupain.model.Configuration as ModelConfiguration

@Serializable(ConfigurationSerializer::class)
interface Configuration {
    val repositories: List<Repository>?
    val pluginRepositories: List<Repository>?
    val versionCatalogPath: Path?
    val excludedKeys: Set<String>?
    val excludedLibraries: List<LibraryExclusion>?
    val excludedPlugins: List<PluginExclusion>?
    val policy: String?
    val policyPluginDir: Path?
    val cacheDir: Path?
    val outputType: OutputType?
    val outputPath: Path?
    val gradleWrapperPropertiesPath: Path?
    val onlyCheckStaticVersions: Boolean?

    fun toConfiguration(baseConfiguration: ModelConfiguration): ModelConfiguration

    enum class OutputType {
        CONSOLE, HTML, MARKDOWN
    }
}