@file:UseSerializers(GradleDependencyVersionSerializer::class)

package com.deezer.dependencies.model.versionCatalog

import com.deezer.dependencies.model.GradleDependencyVersion
import com.deezer.dependencies.model.GradleDependencyVersionSerializer
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.peanuuutz.tomlkt.TomlLiteral
import net.peanuuutz.tomlkt.TomlTable
import net.peanuuutz.tomlkt.asTomlDecoder
import kotlin.jvm.JvmInline

sealed interface Version {

    fun isUpdate(version: GradleDependencyVersion.Single): Boolean

    @JvmInline
    value class Simple(val value: GradleDependencyVersion) : Version {
        override fun isUpdate(version: GradleDependencyVersion.Single): Boolean {
            return value.isUpdate(version)
        }
    }

    @Serializable
    data class Rich(
        val require: GradleDependencyVersion? = null,
        val strictly: GradleDependencyVersion? = null,
        val prefer: GradleDependencyVersion? = null,
        val reject: GradleDependencyVersion? = null,
        val rejectAll: Boolean = false
    ) : Version {
        override fun isUpdate(version: GradleDependencyVersion.Single): Boolean {
            return when {
                rejectAll -> false
                reject?.contains(version) == true -> false
                strictly != null -> strictly.isUpdate(version)
                require != null -> when {
                    prefer == null -> require.isUpdate(version)
                    prefer.isUpdate(version) -> true
                    else -> require.isUpdate(version)
                }
                prefer != null -> prefer.isUpdate(version)
                else -> false
            }
        }
    }
}

@OptIn(InternalSerializationApi::class)
class VersionSerializer : KSerializer<Version> {

    private val richSerializer = Version.Rich.serializer()

    override val descriptor = buildSerialDescriptor(
        serialName = "com.deezer.dependencies.model.versionCatalog.Version",
        kind = SerialKind.CONTEXTUAL
    )

    override fun serialize(encoder: Encoder, value: Version) {
        when (value) {
            is Version.Simple -> encoder.encodeString(value.value.text)
            is Version.Rich -> encoder.encodeSerializableValue(richSerializer, value)
        }
    }

    override fun deserialize(decoder: Decoder): Version {
        val tomlDecoder = decoder.asTomlDecoder()
        return when (val tomlElement = tomlDecoder.decodeTomlElement()) {
            is TomlLiteral -> Version.Simple(GradleDependencyVersion(tomlElement.content))
            is TomlTable -> tomlDecoder.toml.decodeFromTomlElement(richSerializer, tomlElement)
            else -> error("Unsupported TOML element type: ${tomlElement::class.simpleName}")
        }
    }
}