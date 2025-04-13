@file:OptIn(InternalSerializationApi::class)

package com.deezer.dependencies.model

import com.deezer.dependencies.model.Dependency.Library
import com.deezer.dependencies.model.Dependency.Plugin
import com.deezer.dependencies.model.versionCatalog.Version
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

internal sealed interface Dependency {

    val group: String?

    val name: String?

    val moduleId: String

    val version: Version?

    val versionRef: String?

    @Serializable(LibrarySerializer::class)
    data class Library(
        override val group: String? = null,
        override val name: String? = null,
        override val version: Version? = null,
        override val versionRef: String? = null
    ) : Dependency {

        override val moduleId: String
            get() = "$group:$name"

        private constructor(
            moduleParts: List<String>,
            version: Version?,
            versionRef: String?
        ) : this(
            group = moduleParts.getOrNull(0),
            name = moduleParts.getOrNull(1),
            version = version,
            versionRef = versionRef
        )

        constructor(
            module: String?,
            version: Version?,
            versionRef: String?
        ) : this(
            moduleParts = module?.split(':').orEmpty(),
            version = version,
            versionRef = versionRef
        )

        private constructor(libraryParts: List<String>) : this(
            group = libraryParts.getOrNull(0),
            name = libraryParts.getOrNull(1),
            version = libraryParts
                .getOrNull(2)
                ?.let { Version.Simple(GradleDependencyVersion(it)) }
        )

        constructor(library: String) : this(libraryParts = library.split(':'))
    }

    @Serializable(PluginSerializer::class)
    data class Plugin(
        val id: String,
        override val version: Version? = null,
        override val versionRef: String? = null
    ) : Dependency {
        override val group: String
            get() = id

        override val name: String = "$id.gradle.plugin"

        override val moduleId: String
            get() = id

        private constructor(pluginParts: List<String>) : this(
            id = pluginParts[0],
            version = pluginParts
                .getOrNull(1)
                ?.let { Version.Simple(GradleDependencyVersion(it)) }
        )

        constructor(plugin: String) : this(plugin.split(':'))
    }
}

internal fun Dependency.getVersion(versionReferences: Map<String, Version>): Version? {
    return version ?: versionRef?.let { versionReferences[it] }
}

@Serializable
private data class RichLibrary(
    val group: String? = null,
    val name: String? = null,
    val module: String? = null,
    val version: Version? = null,
    val versionRef: String? = null
) {
    constructor(library: Library) : this(
        group = library.group,
        name = library.name,
        version = library.version,
        versionRef = library.versionRef
    )

    fun toLibrary(): Library = if (module == null) {
        Library(
            module = module,
            version = version,
            versionRef = versionRef
        )
    } else {
        Library(
            group = group,
            name = name,
            version = version,
            versionRef = versionRef
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
    val versionRef: String? = null
) {
    constructor(plugin: Plugin) : this(
        id = plugin.id,
        version = plugin.version,
        versionRef = plugin.versionRef
    )

    fun toPlugin(): Plugin = Plugin(
        id = id,
        version = version,
        versionRef = versionRef
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