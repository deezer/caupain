/*
 * MIT License
 *
 * Copyright (c) 2026 Deezer
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
 *
 */

package com.deezer.caupain.cli.serialization

import com.deezer.caupain.model.GradleDependencyVersion
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.peanuuutz.tomlkt.TomlElement
import net.peanuuutz.tomlkt.TomlTable
import com.deezer.caupain.model.Filter as CoreFilter

object FilterSerializer : TomlContentPolymorphicSerializer<CoreFilter>(CoreFilter::class) {

    @Suppress("UNCHECKED_CAST")
    override fun selectSerializer(value: CoreFilter): SerializationStrategy<CoreFilter>? {
        return when (value) {
            is CoreFilter.LibraryFilter -> LibraryFilterSerializer as SerializationStrategy<CoreFilter>
            is CoreFilter.PluginFilter -> PluginFilterSerializer as SerializationStrategy<CoreFilter>
        }
    }

    override fun selectDeserializer(root: TomlElement): DeserializationStrategy<CoreFilter> {
        return if ((root as TomlTable).containsKey("id")) {
            PluginFilterSerializer
        } else {
            LibraryFilterSerializer
        }
    }
}

private object LibraryFilterSerializer : KSerializer<CoreFilter.LibraryFilter> {

    private val delegateSerializer = Filter.LibraryFilter.serializer()

    override val descriptor: SerialDescriptor
        get() = delegateSerializer.descriptor

    override fun serialize(
        encoder: Encoder,
        value: CoreFilter.LibraryFilter
    ) {
        encoder.encodeSerializableValue(
            delegateSerializer,
            Filter.LibraryFilter(value.group, value.name, value.versionFilter)
        )
    }

    override fun deserialize(decoder: Decoder): CoreFilter.LibraryFilter {
        val value = decoder.decodeSerializableValue(delegateSerializer)
        return CoreFilter.LibraryFilter(value.group, value.name, value.versionFilter)
    }
}

private object PluginFilterSerializer : KSerializer<CoreFilter.PluginFilter> {

    private val delegateSerializer = Filter.PluginFilter.serializer()

    override val descriptor: SerialDescriptor
        get() = delegateSerializer.descriptor

    override fun serialize(
        encoder: Encoder,
        value: CoreFilter.PluginFilter
    ) {
        encoder.encodeSerializableValue(
            delegateSerializer,
            Filter.PluginFilter(value.id, value.versionFilter)
        )
    }

    override fun deserialize(decoder: Decoder): CoreFilter.PluginFilter {
        val value = decoder.decodeSerializableValue(delegateSerializer)
        return CoreFilter.PluginFilter(value.id, value.versionFilter)
    }
}

private sealed interface Filter {

    val versionFilter: GradleDependencyVersion

    @Serializable
    data class PluginFilter(
        val id: String,
        override val versionFilter: GradleDependencyVersion,
    ) : Filter

    @Serializable
    data class LibraryFilter(
        val group: String,
        val name: String? = null,
        override val versionFilter: GradleDependencyVersion,
    ) : Filter
}
