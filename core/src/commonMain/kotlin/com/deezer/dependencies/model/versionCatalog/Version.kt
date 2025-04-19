package com.deezer.dependencies.model.versionCatalog

import com.deezer.dependencies.model.GradleDependencyVersion
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.peanuuutz.tomlkt.TomlLiteral
import net.peanuuutz.tomlkt.TomlTable
import net.peanuuutz.tomlkt.asTomlDecoder

/**
 * Represents a version in the version catalog. See the [Gradle documentation](https://docs.gradle.org/current/userguide/dependency_versions.html)
 * for more information.
 */
@Serializable(VersionSerializer::class)
public sealed interface Version {

    /**
     * Resolves the version based on the provided version references.
     *
     * @param versionReferences A map of version references to their corresponding [Resolved] versions.
     * @return The resolved [Resolved] version, or null if not found.
     */
    public fun resolve(versionReferences: Map<String, Resolved>): Resolved? {
        return when (this) {
            is Simple -> this
            is Reference -> versionReferences[ref]
            is Rich -> this
        }
    }

    /**
     * Interface for resolved versions.
     */
    @Serializable(DirectVersionSerializer::class)
    public sealed interface Resolved : Version {
        public fun isUpdate(version: GradleDependencyVersion.Single): Boolean
    }

    /**
     * Represents a simple version.
     */
    public data class Simple(val value: GradleDependencyVersion) : Resolved {
        override fun isUpdate(version: GradleDependencyVersion.Single): Boolean {
            return value.isUpdate(version)
        }

        override fun toString(): String = value.toString()
    }

    /**
     * Represents a version reference.
     *
     * @param ref The reference key
     */
    @Serializable
    public data class Reference(val ref: String) : Version

    /**
     * Represents a rich version with various constraints.
     */
    @Serializable
    public data class Rich(
        val require: GradleDependencyVersion? = null,
        val strictly: GradleDependencyVersion? = null,
        val prefer: GradleDependencyVersion? = null,
        val reject: GradleDependencyVersion? = null,
        val rejectAll: Boolean = false
    ) : Resolved {
        val probableSelectedVersion: GradleDependencyVersion.Single?
            get() = if (rejectAll) {
                null
            } else {
                strictly as? GradleDependencyVersion.Single
                    ?: require as? GradleDependencyVersion.Single
                    ?: prefer as? GradleDependencyVersion.Single
            }

        override fun isUpdate(version: GradleDependencyVersion.Single): Boolean {
            return when {
                rejectAll -> false
                reject?.contains(version) == true -> false
                strictly != null -> if (strictly !is GradleDependencyVersion.Single && prefer != null) {
                    prefer.isUpdate(version)
                } else {
                    strictly.isUpdate(version)
                }

                require != null -> when {
                    prefer == null -> require.isUpdate(version)
                    prefer.isUpdate(version) -> true
                    else -> require.isUpdate(version)
                }

                prefer != null -> prefer.isUpdate(version)
                else -> false
            }
        }

        override fun toString(): String = sequenceOf(
            Constraint.Version("require", require),
            Constraint.Version("strictly", strictly),
            Constraint.Version("prefer", prefer),
            Constraint.Version("reject", reject),
            Constraint.RejectAll(rejectAll)
        ).mapNotNull { it.printValue }.joinToString(
            prefix = "{ ",
            postfix = " }",
            separator = ", "
        )

        private sealed interface Constraint {

            val printValue: String?

            data class Version(
                private val name: String,
                private val version: GradleDependencyVersion?
            ) : Constraint {
                override val printValue: String? = version?.let { "$name = $it" }
            }

            data class RejectAll(private val rejectAll: Boolean) : Constraint {
                override val printValue: String? = if (rejectAll) "rejectAll = true" else null
            }
        }
    }
}

@OptIn(InternalSerializationApi::class)
internal class VersionSerializer : KSerializer<Version> {

    private val richSerializer = Version.Rich.serializer()

    private val referenceSerializer = Version.Reference.serializer()

    override val descriptor = buildSerialDescriptor(
        serialName = "com.deezer.dependencies.model.versionCatalog.Version",
        kind = SerialKind.CONTEXTUAL
    )

    override fun serialize(encoder: Encoder, value: Version) {
        when (value) {
            is Version.Simple -> encoder.encodeString(value.value.text)

            is Version.Rich -> encoder.encodeSerializableValue(richSerializer, value)

            is Version.Reference -> encoder.encodeSerializableValue(referenceSerializer, value)
        }
    }

    override fun deserialize(decoder: Decoder): Version {
        val tomlDecoder = decoder.asTomlDecoder()
        return when (val tomlElement = tomlDecoder.decodeTomlElement()) {
            is TomlLiteral -> Version.Simple(GradleDependencyVersion(tomlElement.content))

            is TomlTable -> tomlDecoder
                .toml
                .decodeFromTomlElement(
                    deserializer = if ("ref" in tomlElement) {
                        referenceSerializer
                    } else {
                        richSerializer
                    },
                    element = tomlElement
                )

            else -> error("Unsupported TOML element type: ${tomlElement::class.simpleName}")
        }
    }
}

@OptIn(InternalSerializationApi::class)
internal class DirectVersionSerializer : KSerializer<Version.Resolved> {

    private val richSerializer = Version.Rich.serializer()

    override val descriptor = buildSerialDescriptor(
        serialName = "com.deezer.dependencies.model.versionCatalog.Version.Direct",
        kind = SerialKind.CONTEXTUAL
    )

    override fun serialize(encoder: Encoder, value: Version.Resolved) {
        when (value) {
            is Version.Simple -> encoder.encodeString(value.value.text)
            is Version.Rich -> encoder.encodeSerializableValue(richSerializer, value)
        }
    }

    override fun deserialize(decoder: Decoder): Version.Resolved {
        val tomlDecoder = decoder.asTomlDecoder()
        return when (val tomlElement = tomlDecoder.decodeTomlElement()) {
            is TomlLiteral -> Version.Simple(GradleDependencyVersion(tomlElement.content))
            is TomlTable -> tomlDecoder.toml.decodeFromTomlElement(richSerializer, tomlElement)
            else -> error("Unsupported TOML element type: ${tomlElement::class.simpleName}")
        }
    }
}