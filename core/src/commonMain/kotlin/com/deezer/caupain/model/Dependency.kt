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

@file:OptIn(InternalSerializationApi::class)

package com.deezer.caupain.model

import com.deezer.caupain.model.Dependency.Library
import com.deezer.caupain.model.Dependency.Plugin
import com.deezer.caupain.model.versionCatalog.Version
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.peanuuutz.tomlkt.TomlLiteral
import net.peanuuutz.tomlkt.TomlTable
import net.peanuuutz.tomlkt.asTomlDecoder

/**
 * Describes a dependency from the version catalog.
 */
public sealed interface Dependency {


    /**
     * The dependency id
     */
    public val moduleId: String

    /**
     * The dependency current version
     */
    public val version: Version?

    /**
     * Library dependency
     */
    @Serializable(LibrarySerializer::class)
    public data class Library(
        public val group: String? = null,
        public val name: String? = null,
        override val version: Version? = null,
    ) : Dependency {

        override val moduleId: String
            get() = "$group:$name"

        private constructor(
            moduleParts: List<String>,
            version: Version? = null,
        ) : this(
            group = moduleParts.getOrNull(0),
            name = moduleParts.getOrNull(1),
            version = version,
        )

        internal constructor(
            module: String?,
            version: Version? = null,
        ) : this(
            moduleParts = module?.split(':').orEmpty(),
            version = version,
        )

        private constructor(libraryParts: List<String>) : this(
            group = libraryParts.getOrNull(0),
            name = libraryParts.getOrNull(1),
            version = libraryParts
                .getOrNull(2)
                ?.let { Version.Simple(GradleDependencyVersion(it)) }
        )

        internal constructor(library: String) : this(libraryParts = library.split(':'))
    }

    /**
     * Plugin dependency
     */
    @Serializable(PluginSerializer::class)
    public data class Plugin(
        public val id: String,
        override val version: Version? = null,
    ) : Dependency {

        override val moduleId: String
            get() = id

        private constructor(pluginParts: List<String>) : this(
            id = pluginParts[0],
            version = pluginParts
                .getOrNull(1)
                ?.let { Version.Simple(GradleDependencyVersion(it)) }
        )

        internal constructor(plugin: String) : this(plugin.split(':'))
    }
}

internal val Dependency.group: String?
    get() = when (this) {
        is Library -> group
        is Plugin -> id
    }

internal val Dependency.name: String?
    get() = when (this) {
        is Library -> name
        is Plugin -> "$id.gradle.plugin"
    }

@Serializable
private data class RichLibrary(
    val group: String? = null,
    val name: String? = null,
    val module: String? = null,
    val version: Version? = null,
) {
    constructor(library: Library) : this(
        group = library.group,
        name = library.name,
        version = library.version,
    )

    fun toLibrary(): Library = if (module == null) {
        Library(
            group = group,
            name = name,
            version = version,
        )
    } else {
        Library(
            module = module,
            version = version,
        )
    }
}

internal object LibrarySerializer : KSerializer<Library> {

    private val richSerializer = RichLibrary.serializer()

    override val descriptor: SerialDescriptor = buildSerialDescriptor(
        serialName = "com.deezer.dependencies.model.versionCatalog.Dependency",
        kind = SerialKind.CONTEXTUAL
    )

    override fun serialize(encoder: Encoder, value: Library) {
        encoder.encodeSerializableValue(richSerializer, RichLibrary(value))
    }

    override fun deserialize(decoder: Decoder): Library {
        val tomlDecoder = decoder.asTomlDecoder()
        return when (val element = tomlDecoder.decodeTomlElement()) {
            is TomlLiteral -> Library(element.content)

            is TomlTable -> tomlDecoder
                .toml
                .decodeFromTomlElement(richSerializer, element)
                .toLibrary()

            else -> error("Unsupported TOML element type: ${element::class.simpleName}")
        }
    }
}

@Serializable
private data class RichPlugin(
    val id: String,
    val version: Version? = null,
) {
    constructor(plugin: Plugin) : this(
        id = plugin.id,
        version = plugin.version,
    )

    fun toPlugin(): Plugin = Plugin(
        id = id,
        version = version,
    )
}

internal object PluginSerializer : KSerializer<Plugin> {

    private val richSerializer = RichPlugin.serializer()

    override val descriptor: SerialDescriptor = buildSerialDescriptor(
        serialName = "com.deezer.dependencies.model.versionCatalog.Plugin",
        kind = SerialKind.CONTEXTUAL
    )

    override fun serialize(encoder: Encoder, value: Plugin) {
        encoder.encodeSerializableValue(richSerializer, RichPlugin(value))
    }

    override fun deserialize(decoder: Decoder): Plugin {
        val tomlDecoder = decoder.asTomlDecoder()
        return when (val element = tomlDecoder.decodeTomlElement()) {
            is TomlLiteral -> Plugin(element.content)

            is TomlTable -> tomlDecoder
                .toml
                .decodeFromTomlElement(richSerializer, element)
                .toPlugin()

            else -> error("Unsupported TOML element type: ${element::class.simpleName}")
        }
    }
}