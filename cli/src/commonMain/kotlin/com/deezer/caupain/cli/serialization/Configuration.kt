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
    OutputTypeSerializer::class,
)

package com.deezer.caupain.cli.serialization

import com.deezer.caupain.cli.internal.CAN_USE_PLUGINS
import com.deezer.caupain.cli.model.Configuration
import com.deezer.caupain.cli.model.Repository
import com.deezer.caupain.model.LibraryExclusion
import com.deezer.caupain.model.Logger
import com.deezer.caupain.model.PluginExclusion
import com.deezer.caupain.model.gradle.GradleStabilityLevel
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import okio.Path

@Serializable
private data class ConfigurationImpl(
    override val repositories: List<Repository>? = null,
    override val pluginRepositories: List<Repository>? = null,
    override val versionCatalogPath: Path? = null,
    override val versionCatalogPaths: List<Path>? = null,
    override val excludedKeys: Set<String>? = null,
    override val excludedLibraries: List<LibraryExclusion>? = null,
    override val excludedPlugins: List<PluginExclusion>? = null,
    override val policy: String? = null,
    override val policyPluginDir: Path? = null,
    override val cacheDir: Path? = null,
    override val showVersionReferences: Boolean? = null,
    override val outputType: Configuration.OutputType? = null,
    override val outputTypes: Set<Configuration.OutputType>? = null,
    override val outputPath: Path? = null,
    override val outputDir: Path?,
    override val outputBaseName: String?,
    override val gradleWrapperPropertiesPath: Path? = null,
    override val onlyCheckStaticVersions: Boolean? = null,
    override val gradleStabilityLevel: GradleStabilityLevel? = null,
    override val checkIgnored: Boolean? = null,
    override val githubToken: String? = null,
    override val searchReleaseNote: Boolean? = null,
    override val verifyExistence: Boolean? = null
) : Configuration {

    override fun validate(logger: Logger) {
        if (versionCatalogPath != null && versionCatalogPaths != null) {
            logger.warn("Both versionCatalogPath and versionCatalogPaths are set. Using versionCatalogPaths.")
        }
        if (outputType != null && outputTypes != null) {
            logger.warn("Both outputType and outputTypes are set. Using outputTypes.")
        }
        if (outputPath != null) {
            val hasMultipleFileOutputs = outputTypes
                ?.let { types ->
                    types.count { it != Configuration.OutputType.CONSOLE } > 1
                } == true
            if (hasMultipleFileOutputs) {
                logger.warn("outputPath will be ignored because multiple file output types are set in outputTypes.")
            }
        }
        if (policyPluginDir != null && !CAN_USE_PLUGINS) {
            logger.error("Policy plugins are not supported on this platform")
        }
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