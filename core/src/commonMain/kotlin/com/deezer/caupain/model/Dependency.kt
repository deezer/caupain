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

package com.deezer.caupain.model

import com.deezer.caupain.model.Dependency.Library
import com.deezer.caupain.model.Dependency.Plugin
import com.deezer.caupain.model.versionCatalog.Version
import dev.drewhamilton.poko.Poko
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import net.peanuuutz.tomlkt.TomlDecoder
import net.peanuuutz.tomlkt.TomlLiteral
import net.peanuuutz.tomlkt.TomlTable

/**
 * Describes a dependency from the version catalog.
 */
@Serializable
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
     * Returns a new dependency with resolved versions from the given version references.
     */
    public fun resolved(versionReferences: Map<String, Version.Resolved>): Dependency

    /**
     * Library dependency
     */
    @Serializable(LibrarySerializer::class)
    @Poko
    public class Library(
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

        override fun resolved(versionReferences: Map<String, Version.Resolved>): Dependency {
            return if (version is Version.Reference) {
                Library(
                    group = group,
                    name = name,
                    version = version.resolve(versionReferences)
                )
            } else {
                this
            }
        }
    }

    /**
     * Plugin dependency
     */
    @Serializable(PluginSerializer::class)
    @Poko
    public class Plugin(
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

        override fun resolved(versionReferences: Map<String, Version.Resolved>): Dependency {
            return if (version is Version.Reference) {
                Plugin(
                    id = id,
                    version = version.resolve(versionReferences)
                )
            } else {
                this
            }
        }
    }
}

internal data class KeyedDependency(
    val key: String,
    val dependency: Dependency,
) {
    constructor(entry: Map.Entry<String, Dependency>) : this(
        key = entry.key,
        dependency = entry.value,
    )
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

    override val descriptor: SerialDescriptor = SerialDescriptor(
        serialName = "library",
        original = richSerializer.descriptor,
    )

    override fun serialize(encoder: Encoder, value: Library) {
        encoder.encodeSerializableValue(richSerializer, RichLibrary(value))
    }

    override fun deserialize(decoder: Decoder): Library {
        return if (decoder is TomlDecoder) {
            // When parsing from version catalog, library can be just a string
            when (val element = decoder.decodeTomlElement()) {
                is TomlLiteral -> Library(library = element.content)

                is TomlTable -> decoder
                    .toml
                    .decodeFromTomlElement(richSerializer, element)
                    .toLibrary()

                else -> error("Unsupported TOML element type: ${element::class.simpleName}")
            }
        } else {
            decoder.decodeSerializableValue(richSerializer).toLibrary()
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

    override val descriptor: SerialDescriptor = SerialDescriptor(
        serialName = "plugin",
        original = richSerializer.descriptor,
    )

    override fun serialize(encoder: Encoder, value: Plugin) {
        encoder.encodeSerializableValue(richSerializer, RichPlugin(value))
    }

    override fun deserialize(decoder: Decoder): Plugin {
        return if (decoder is TomlDecoder) {
            // When parsing from version catalog, plugin can be just a string
            when (val element = decoder.decodeTomlElement()) {
                is TomlLiteral -> Plugin(element.content)

                is TomlTable -> decoder
                    .toml
                    .decodeFromTomlElement(richSerializer, element)
                    .toPlugin()

                else -> error("Unsupported TOML element type: ${element::class.simpleName}")
            }
        } else {
            decoder.decodeSerializableValue(richSerializer).toPlugin()
        }
    }
}
