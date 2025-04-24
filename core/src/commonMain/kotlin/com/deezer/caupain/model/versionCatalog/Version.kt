package com.deezer.caupain.model.versionCatalog

import com.deezer.caupain.model.GradleDependencyVersion
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

        /**
         * Whether or not this represents a static version (either a version like `1.0.0` or a snapshot version).
         */
        public val isStatic: Boolean

        public fun isUpdate(version: GradleDependencyVersion.Static): Boolean
    }

    /**
     * Represents a simple version.
     */
    public class Simple(public val value: GradleDependencyVersion) : Resolved {

        override val isStatic: Boolean
            get() = value.isStatic

        override fun isUpdate(version: GradleDependencyVersion.Static): Boolean {
            return value.isUpdate(version)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as Simple

            return value == other.value
        }

        override fun hashCode(): Int {
            return value.hashCode()
        }

        override fun toString(): String = value.toString()
    }

    /**
     * Represents a version reference.
     *
     * @param ref The reference key
     */
    @Serializable
    public class Reference(public val ref: String) : Version {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as Reference

            return ref == other.ref
        }

        override fun hashCode(): Int {
            return ref.hashCode()
        }

        override fun toString(): String {
            return "Reference(ref='$ref')"
        }
    }

    /**
     * Represents a rich version with various constraints.
     */
    @Serializable
    public class Rich(
        public val require: GradleDependencyVersion? = null,
        public val strictly: GradleDependencyVersion? = null,
        public val prefer: GradleDependencyVersion? = null,
        public val reject: GradleDependencyVersion? = null,
        public val rejectAll: Boolean = false
    ) : Resolved {

        override val isStatic: Boolean
            get() = false

        /**
         * Returns the most probable selected fixed version based on the constraints.
         */
        public val probableSelectedVersion: GradleDependencyVersion.Static?
            get() = if (rejectAll) {
                null
            } else {
                strictly as? GradleDependencyVersion.Static
                    ?: require as? GradleDependencyVersion.Static
                    ?: prefer as? GradleDependencyVersion.Static
            }

        override fun isUpdate(version: GradleDependencyVersion.Static): Boolean {
            return when {
                rejectAll -> false
                reject?.contains(version) == true -> false
                strictly != null -> if (strictly !is GradleDependencyVersion.Static && prefer != null) {
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

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as Rich

            if (rejectAll != other.rejectAll) return false
            if (require != other.require) return false
            if (strictly != other.strictly) return false
            if (prefer != other.prefer) return false
            if (reject != other.reject) return false

            return true
        }

        override fun hashCode(): Int {
            var result = rejectAll.hashCode()
            result = 31 * result + (require?.hashCode() ?: 0)
            result = 31 * result + (strictly?.hashCode() ?: 0)
            result = 31 * result + (prefer?.hashCode() ?: 0)
            result = 31 * result + (reject?.hashCode() ?: 0)
            return result
        }

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