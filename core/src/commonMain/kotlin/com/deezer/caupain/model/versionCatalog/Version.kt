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

@file:OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)

package com.deezer.caupain.model.versionCatalog

import com.deezer.caupain.model.GradleDependencyVersion
import dev.drewhamilton.poko.Poko
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.peanuuutz.tomlkt.TomlDecoder
import net.peanuuutz.tomlkt.TomlLiteral
import net.peanuuutz.tomlkt.TomlTable

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

        /**
         * Checks is [version] is an update compared to this version.
         */
        public fun isUpdate(version: GradleDependencyVersion.Static): Boolean
    }

    /**
     * Represents a simple version.
     */
    @Serializable
    @Poko
    public class Simple(public val value: GradleDependencyVersion) : Resolved {

        override val isStatic: Boolean
            get() = value.isStatic

        override fun isUpdate(version: GradleDependencyVersion.Static): Boolean {
            return value.isUpdate(version)
        }

        override fun toString(): String = value.toString()
    }

    /**
     * Represents a version reference.
     *
     * @property ref The reference key
     */
    @Serializable
    @Poko
    public class Reference(public val ref: String) : Version

    /**
     * Represents a rich version with various constraints.
     */
    @Serializable
    @Poko
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

internal class VersionSerializer : KSerializer<Version> {

    private val richSerializer = Version.Rich.serializer()

    private val referenceSerializer = Version.Reference.serializer()

    override val descriptor = buildSerialDescriptor(
        serialName = "com.deezer.dependencies.model.versionCatalog.Version",
        kind = PolymorphicKind.SEALED
    )

    override fun serialize(encoder: Encoder, value: Version) {
        when (value) {
            is Version.Simple -> encoder.encodeString(value.value.text)

            is Version.Rich -> encoder.encodeSerializableValue(richSerializer, value)

            is Version.Reference -> encoder.encodeSerializableValue(referenceSerializer, value)
        }
    }

    override fun deserialize(decoder: Decoder): Version {
        return when (decoder) {
            is TomlDecoder -> when (val element = decoder.decodeTomlElement()) {
                is TomlLiteral -> Version.Simple(GradleDependencyVersion(element.content))

                is TomlTable -> decoder
                    .toml
                    .decodeFromTomlElement(
                        deserializer = if ("ref" in element) {
                            referenceSerializer
                        } else {
                            richSerializer
                        },
                        element = element
                    )

                else -> error("Unsupported TOML element type: ${element::class.simpleName}")
            }

            is JsonDecoder -> when (val element = decoder.decodeJsonElement()) {
                is JsonPrimitive -> Version.Simple(GradleDependencyVersion(element.content))

                is JsonObject -> decoder
                    .json
                    .decodeFromJsonElement(
                        deserializer = if ("ref" in element) {
                            referenceSerializer
                        } else {
                            richSerializer
                        },
                        element = element
                    )

                else -> error("Unsupported JSON element type: ${element::class.simpleName}")
            }

            else -> error("Unsupported decoder ${decoder::class.simpleName}")
        }
    }
}

internal class DirectVersionSerializer : KSerializer<Version.Resolved> {

    private val richSerializer = Version.Rich.serializer()

    override val descriptor = buildSerialDescriptor(
        serialName = "com.deezer.dependencies.model.versionCatalog.Version.Direct",
        kind = PolymorphicKind.SEALED
    )

    override fun serialize(encoder: Encoder, value: Version.Resolved) {
        when (value) {
            is Version.Simple -> encoder.encodeString(value.value.text)
            is Version.Rich -> encoder.encodeSerializableValue(richSerializer, value)
        }
    }

    override fun deserialize(decoder: Decoder): Version.Resolved {
        return when (decoder) {
            is TomlDecoder -> when (val element = decoder.decodeTomlElement()) {
                is TomlLiteral -> Version.Simple(GradleDependencyVersion(element.content))
                is TomlTable -> decoder.toml.decodeFromTomlElement(richSerializer, element)
                else -> error("Unsupported TOML element type: ${element::class.simpleName}")
            }

            is JsonDecoder -> when (val element = decoder.decodeJsonElement()) {
                is JsonPrimitive -> Version.Simple(GradleDependencyVersion(element.content))
                is JsonObject -> decoder.json.decodeFromJsonElement(richSerializer, element)
                else -> error("Unsupported JSON element type: ${element::class.simpleName}")
            }

            else -> error("Unsupported decoder ${decoder::class.simpleName}")
        }
    }
}
