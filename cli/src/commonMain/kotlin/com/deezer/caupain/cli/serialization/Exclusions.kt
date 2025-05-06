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

package com.deezer.caupain.cli.serialization

import com.deezer.caupain.model.LibraryExclusion
import com.deezer.caupain.model.PluginExclusion
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
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

@Suppress("UnnecessaryOptInAnnotation")
@OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
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

object PluginExclusionSerializer : KSerializer<PluginExclusion> {
    override val descriptor = PrimitiveSerialDescriptor("okio.Path", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): PluginExclusion =
        PluginExclusion(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: PluginExclusion) {
        encoder.encodeString(value.id)
    }
}