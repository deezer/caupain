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

@file:UseSerializers(
    RepositorySerializer::class,
    PathSerializer::class,
    PluginExclusionSerializer::class,
    LibraryExclusionSerializer::class,
)

package com.deezer.caupain.cli.serialization

import com.deezer.caupain.cli.model.Configuration
import com.deezer.caupain.cli.model.Repository
import com.deezer.caupain.model.LibraryExclusion
import com.deezer.caupain.model.PluginExclusion
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
    override val versionCatalogPaths: Iterable<Path>? = null,
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
            repositories = repositories?.map { it.toModel() }
                ?: baseConfiguration.repositories,
            pluginRepositories = pluginRepositories?.map { it.toModel() }
                ?: baseConfiguration.pluginRepositories,
            versionCatalogPaths = versionCatalogPaths
                ?: versionCatalogPath?.let(::listOf)
                ?: baseConfiguration.versionCatalogPaths,
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