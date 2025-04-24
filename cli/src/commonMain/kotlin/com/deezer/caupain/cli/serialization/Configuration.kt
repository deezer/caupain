@file:UseSerializers(
    RepositorySerializer::class,
    PathSerializer::class,
    PluginExclusionSerializer::class,
    LibraryExclusionSerializer::class,
)

package com.deezer.caupain.cli.serialization

import com.deezer.caupain.cli.model.Configuration
import com.deezer.caupain.model.LibraryExclusion
import com.deezer.caupain.model.PluginExclusion
import com.deezer.caupain.model.Repository
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import okio.Path
import com.deezer.caupain.model.Configuration as ModelConfiguration

@Serializable
private data class ConfigurationImpl(
    override val repositories: List<Repository>? = null,
    override val pluginRepositories: List<Repository>? = null,
    override val versionCatalogPath: Path? = null,
    override val excludedKeys: Set<String>? = null,
    override val excludedLibraries: List<LibraryExclusion>? = null,
    override val excludedPlugins: List<PluginExclusion>? = null,
    override val policy: String? = null,
    override val policyPluginDir: Path? = null,
    override val cacheDir: Path? = null,
    @Serializable(OutputTypeSerializer::class) override val outputType: Configuration.OutputType? = Configuration.OutputType.CONSOLE,
    override val outputPath: Path?,
    override val gradleWrapperPropertiesPath: Path?,
    override val onlyCheckStaticVersions: Boolean?
) : Configuration {
    override fun toConfiguration(baseConfiguration: ModelConfiguration): ModelConfiguration {
        return ModelConfiguration(
            repositories = repositories ?: baseConfiguration.repositories,
            pluginRepositories = pluginRepositories ?: baseConfiguration.pluginRepositories,
            versionCatalogPath = versionCatalogPath ?: baseConfiguration.versionCatalogPath,
            excludedKeys = excludedKeys ?: baseConfiguration.excludedKeys,
            excludedLibraries = excludedLibraries ?: baseConfiguration.excludedLibraries,
            excludedPlugins = excludedPlugins ?: baseConfiguration.excludedPlugins,
            policy = policy ?: baseConfiguration.policy,
            policyPluginsDir = policyPluginDir ?: baseConfiguration.policyPluginsDir,
            cacheDir = cacheDir ?: baseConfiguration.cacheDir,
            onlyCheckStaticVersions = onlyCheckStaticVersions
                ?: baseConfiguration.onlyCheckStaticVersions
        )
    }
}

object ConfigurationSerializer : KSerializer<Configuration> {
    private val implSerializer = ConfigurationImpl.serializer()

    override val descriptor: SerialDescriptor
        get() = implSerializer.descriptor

    override fun deserialize(decoder: Decoder): Configuration =
        decoder.decodeSerializableValue(implSerializer)

    override fun serialize(encoder: Encoder, value: Configuration) {
        encoder.encodeSerializableValue(implSerializer, value as ConfigurationImpl)
    }
}

private object OutputTypeSerializer : KSerializer<Configuration.OutputType> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("OutputType", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Configuration.OutputType {
        return Configuration.OutputType.valueOf(
            decoder.decodeString().uppercase()
        )
    }

    override fun serialize(
        encoder: Encoder,
        value: Configuration.OutputType
    ) {
        encoder.encodeString(value.name)
    }
}