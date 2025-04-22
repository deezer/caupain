@file:UseSerializers(RepositorySerializer::class, LibraryExclusionSerializer::class)

package com.deezer.caupain.cli.serialization

import com.deezer.caupain.model.LibraryExclusion
import com.deezer.caupain.model.PluginExclusion
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.peanuuutz.tomlkt.TomlLiteral
import net.peanuuutz.tomlkt.TomlTable
import net.peanuuutz.tomlkt.asTomlDecoder
import okio.Path
import okio.Path.Companion.toPath

@Serializable
private data class RichLibraryExclusion(
    val group: String,
    val name: String? = null,
) {
    constructor(libraryExclusion: LibraryExclusion) : this(
        group = libraryExclusion.group,
        name = libraryExclusion.name,
    )

    fun toLibraryExclusion() = LibraryExclusion(
        group = group,
        name = name
    )
}

@OptIn(InternalSerializationApi::class)
object LibraryExclusionSerializer : KSerializer<LibraryExclusion> {

    private val richSerializer = RichLibraryExclusion.serializer()

    override val descriptor: SerialDescriptor = buildSerialDescriptor(
        serialName = "com.deezer.dependencies.model.LibraryExclusion",
        kind = SerialKind.CONTEXTUAL,
    )

    override fun deserialize(decoder: Decoder): LibraryExclusion {
        val tomlDecoder = decoder.asTomlDecoder()
        return when (val element = tomlDecoder.decodeTomlElement()) {
            is TomlLiteral -> {
                val parts = element.content.split(':')
                LibraryExclusion(
                    group = parts[0],
                    name = parts.getOrNull(1)
                )
            }

            is TomlTable -> tomlDecoder
                .toml
                .decodeFromTomlElement(richSerializer, element)
                .toLibraryExclusion()

            else ->
                throw SerializationException("Unsupported toml element type: ${element::class.simpleName}")
        }
    }

    override fun serialize(encoder: Encoder, value: LibraryExclusion) {
        encoder.encodeSerializableValue(richSerializer, RichLibraryExclusion(value))
    }
}

object PathSerializer : KSerializer<Path> {
    override val descriptor = PrimitiveSerialDescriptor("okio.Path", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Path = decoder.decodeString().toPath()

    override fun serialize(encoder: Encoder, value: Path) {
        encoder.encodeString(value.toString())
    }
}

object PluginExclusionSerializer : KSerializer<PluginExclusion> {
    override val descriptor = PrimitiveSerialDescriptor("okio.Path", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): PluginExclusion =
        PluginExclusion(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: PluginExclusion) {
        encoder.encodeString(value.id)
    }
}