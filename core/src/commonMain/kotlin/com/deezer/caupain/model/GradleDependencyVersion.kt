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

import com.deezer.caupain.model.GradleDependencyVersion.Exact
import com.deezer.caupain.model.GradleDependencyVersion.Prefix
import com.deezer.caupain.model.GradleDependencyVersion.Range
import com.deezer.caupain.model.GradleDependencyVersion.Snapshot
import com.deezer.caupain.model.GradleDependencyVersion.Unknown
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.jvm.JvmInline

/**
 * Gradle version. See the [Gradle documentation](https://docs.gradle.org/current/userguide/dependency_versions.html#sec:single-version-declarations)
 * for more information.
 */
@Serializable(GradleDependencyVersionSerializer::class)
public sealed interface GradleDependencyVersion {

    /**
     * Whether or not this represents a static version (either a version like `1.0.0` or a snapshot version).
     */
    public val isStatic: Boolean

    /**
     * The text representation of the version.
     */
    public val text: String

    /**
     * Interface for versions representing a static version.
     */
    @Serializable(GradleDependencyVersionStaticSerializer::class)
    public sealed interface Static : GradleDependencyVersion, Comparable<Static> {

        override val isStatic: Boolean
            get() = true

        /**
         * The corresponding exact version of the dependency.
         */
        public val exactVersion: Exact
    }

    /**
     * Checks if the given version is contained within this version.
     */
    public operator fun contains(version: Static): Boolean

    /**
     * Checks if the given version is an update compared to this version.
     */
    public fun isUpdate(version: Static): Boolean

    /**
     * Exact version
     */
    public class Exact(private val version: String) : Static {

        override val text: String
            get() = version

        override val exactVersion: Exact
            get() = this

        // We only use this for comparison, so we don't need to load it eagerly
        private val parts by lazy { toParts() }

        override fun contains(version: Static): Boolean = this == version

        // Dependency version compared like in https://docs.gradle.org/current/userguide/dependency_versions.html#sec:version-ordering
        override fun compareTo(other: Static): Int = when (other) {
            is Exact -> compareTo(other)
            is Snapshot -> {
                val compareResult = compareTo(other.exactVersion)
                if (compareResult == 0) -1 else compareResult
            }
        }

        /**
         * Compares this version with another [Exact] version.
         */
        public fun compareTo(other: Exact): Int {
            val otherParts = other.parts
            val commonLength = minOf(parts.size, otherParts.size)
            for (i in 0 until commonLength) {
                val partComparison = parts[i].compareTo(otherParts[i])
                if (partComparison != 0) return partComparison
            }
            return when {
                parts.size > otherParts.size -> when (parts[commonLength]) {
                    is Part.Numeric -> 1
                    is Part.Alphabetical -> -1
                }

                parts.size < otherParts.size -> when (otherParts[commonLength]) {
                    is Part.Numeric -> -1
                    is Part.Alphabetical -> 1
                }

                else -> 0
            }
        }

        override fun isUpdate(version: Static): Boolean = when (version) {
            is Snapshot -> version.exactVersion >= this
            is Exact -> version > this
        }

        private fun toParts(): List<Part> = version
            .splitToSequence(delimiters = SEPARATORS)
            .flatMap { part ->
                sequence {
                    val buf = StringBuilder()
                    var type: Part.Type? = null
                    for (c in part) {
                        val cType = if (c.isDigit()) Part.Type.NUMERIC else Part.Type.ALPHABETICAL
                        if (cType != type) {
                            if (buf.isNotEmpty() && type != null) {
                                yield(Part.from(buf.toString(), type))
                                buf.clear()
                            }
                        }
                        buf.append(c)
                        type = cType
                    }
                    if (buf.isNotEmpty() && type != null) yield(Part.from(buf.toString(), type))
                }
            }
            .toList()

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as Exact

            return version == other.version
        }

        override fun hashCode(): Int {
            return version.hashCode()
        }

        override fun toString(): String = version

        private sealed interface Part : Comparable<Part> {

            @JvmInline
            value class Numeric(val value: Long) : Part {
                override fun compareTo(other: Part): Int {
                    return when (other) {
                        is Numeric -> value.compareTo(other.value)
                        is Alphabetical -> 1
                    }
                }
            }

            data class Alphabetical(val value: String) : Part {
                override fun compareTo(other: Part): Int {
                    return when (other) {
                        is Numeric -> -1

                        is Alphabetical if value == other.value -> 0

                        is Alphabetical if value == DEV -> -1

                        is Alphabetical if other.value == DEV -> 1

                        is Alphabetical -> {
                            val specialValuesIndex = value.specialValuesIndex()
                            val otherSpecialValuesIndex = other.value.specialValuesIndex()
                            when {
                                specialValuesIndex < 0 && otherSpecialValuesIndex < 0 ->
                                    value.compareTo(other.value)

                                specialValuesIndex >= 0 && otherSpecialValuesIndex < 0 -> 1

                                specialValuesIndex < 0 && otherSpecialValuesIndex >= 0 -> -1

                                else -> specialValuesIndex.compareTo(otherSpecialValuesIndex)
                            }
                        }
                    }
                }

                companion object {
                    private const val DEV = "dev"
                    private val SPECIAL_VALUES =
                        listOf("rc", "snapshot", "final", "ga", "release", "sp")

                    private fun String.specialValuesIndex(): Int =
                        indexOfAny(SPECIAL_VALUES, ignoreCase = true)
                }
            }

            enum class Type {
                NUMERIC,
                ALPHABETICAL
            }

            companion object {
                fun from(value: String, type: Type) = when (type) {
                    Type.NUMERIC -> Numeric(value.toLong())
                    Type.ALPHABETICAL -> Alphabetical(value)
                }
            }
        }
    }

    /**
     * Version range
     */
    public class Range(override val text: String) : GradleDependencyVersion {

        override val isStatic: Boolean
            get() = false

        /**
         * Range lower bound
         */
        public val lowerBound: Bound?

        /**
         * Range upper bound
         */
        public val upperBound: Bound?

        init {
            require(text.length > 2 && text.first() in LOWER_BOUND_MARKERS && text.last() in UPPER_BOUND_MARKERS) {
                "Wrong format for range $text"
            }
            val isLowerBoundExclusive = text.first() in EXCLUSIVE_LOWER_BOUND_MARKERS
            val isUpperBoundExclusive = text.last() in EXCLUSIVE_UPPER_BOUND_MARKERS
            val parts = text
                .substring(1, text.lastIndex)
                .split(',')
                .map { it.trim() }
            val lowerBoundText = parts.getOrNull(0)
            lowerBound = if (lowerBoundText.isNullOrBlank()) {
                null
            } else {
                Bound(GradleDependencyVersion(lowerBoundText) as Static, isLowerBoundExclusive)
            }
            val upperBoundText = parts.getOrNull(1)
            upperBound = if (upperBoundText.isNullOrBlank()) {
                null
            } else {
                Bound(GradleDependencyVersion(upperBoundText) as Static, isUpperBoundExclusive)
            }
        }

        @Suppress("CyclomaticComplexMethod") // This applies the documented algorithm
        override fun contains(version: Static): Boolean {
            val exactVersion = version.exactVersion
            return when {
                lowerBound == null && upperBound == null -> false

                lowerBound != null && exactVersion < lowerBound.value -> false

                upperBound != null && exactVersion > upperBound.value -> false

                exactVersion == lowerBound?.value -> !lowerBound.isExclusive

                exactVersion == upperBound?.value -> !upperBound.isExclusive

                lowerBound == null && upperBound != null -> if (upperBound.isExclusive) {
                    exactVersion < upperBound.value
                } else {
                    exactVersion <= upperBound.value
                }

                upperBound == null && lowerBound != null -> if (lowerBound.isExclusive) {
                    exactVersion > lowerBound.value
                } else {
                    exactVersion >= lowerBound.value
                }

                upperBound != null && lowerBound != null -> when {
                    lowerBound.isExclusive && upperBound.isExclusive ->
                        exactVersion > lowerBound.value && exactVersion < upperBound.value

                    lowerBound.isExclusive ->
                        exactVersion > lowerBound.value && exactVersion <= upperBound.value

                    upperBound.isExclusive ->
                        exactVersion >= lowerBound.value && exactVersion < upperBound.value

                    else -> exactVersion >= lowerBound.value && exactVersion <= upperBound.value
                }

                else -> false
            }
        }

        override fun isUpdate(version: Static): Boolean {
            return when (version) {
                in this -> false
                upperBound?.value -> upperBound.isExclusive
                else -> upperBound?.value?.isUpdate(version) == true
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as Range

            return text == other.text
        }

        override fun hashCode(): Int {
            return text.hashCode()
        }

        override fun toString(): String = text

        /**
         * Range bound descriptions
         *
         * @property value bound value
         * @property isExclusive true if the bound is exclusive
         */
        public class Bound(public val value: Static, public val isExclusive: Boolean) {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other == null || this::class != other::class) return false

                other as Bound

                if (isExclusive != other.isExclusive) return false
                if (value != other.value) return false

                return true
            }

            override fun hashCode(): Int {
                var result = isExclusive.hashCode()
                result = 31 * result + value.hashCode()
                return result
            }

            override fun toString(): String {
                return "Bound(value=$value, isExclusive=$isExclusive)"
            }
        }

        internal companion object {
            val LOWER_BOUND_MARKERS = charArrayOf('[', '(', ']')
            val UPPER_BOUND_MARKERS = charArrayOf(']', ')', '[')
            private val EXCLUSIVE_UPPER_BOUND_MARKERS = charArrayOf(')', '[')
            private val EXCLUSIVE_LOWER_BOUND_MARKERS = charArrayOf('(', ']')
        }
    }

    /**
     * Version with prefix
     */
    public class Prefix(override val text: String) : GradleDependencyVersion {

        override val isStatic: Boolean
            get() = false

        private val regex: Regex?

        internal val baseVersion: Exact?

        init {
            require(text.isNotEmpty() && text.last() == '+') {
                "Wrong format for prefix $text"
            }
            val prefixText = text.substring(0, text.lastIndex)
            regex = if (prefixText.isEmpty()) {
                // If the text is just a '+', we match all versions
                null
            } else {
                Regex("${Regex.escape(prefixText)}.*")
            }
            val exactText = when {
                prefixText.isEmpty() -> null
                prefixText.last() in SEPARATORS -> prefixText.substring(0, prefixText.lastIndex)
                else -> prefixText
            }
            baseVersion = exactText?.let(::Exact)
        }

        override fun contains(version: Static): Boolean {
            return regex == null || regex.matches(version.text)
        }

        override fun isUpdate(version: Static): Boolean {
            return baseVersion != null && !contains(version) && baseVersion.isUpdate(version)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as Prefix

            return text == other.text
        }

        override fun hashCode(): Int {
            return text.hashCode()
        }

        override fun toString(): String = text
    }

    /**
     * Latest version
     */
    public class Latest(override val text: String) : GradleDependencyVersion {

        override val isStatic: Boolean
            get() = false

        override fun contains(version: Static): Boolean = false

        override fun isUpdate(version: Static): Boolean = false

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as Latest

            return text == other.text
        }

        override fun hashCode(): Int {
            return text.hashCode()
        }

        override fun toString(): String = text

        internal companion object {
            val VALUES = setOf(
                "latest.integration",
                "latest.release",
            )
        }
    }

    /**
     * Snapshot version
     */
    public class Snapshot(override val text: String) : Static {

        override val exactVersion: Exact = Exact(text.dropLast(SNAPSHOT_SUFFIX.length))

        override fun compareTo(other: Static): Int = when (other) {
            is Snapshot -> exactVersion.compareTo(other.exactVersion)
            is Exact -> {
                val compareResult = exactVersion.compareTo(other)
                if (compareResult == 0) 1 else compareResult
            }
        }

        override fun contains(version: Static): Boolean = this == version

        override fun isUpdate(version: Static): Boolean = version.exactVersion > exactVersion

        override fun toString(): String = text

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as Snapshot

            return text == other.text
        }

        override fun hashCode(): Int {
            return text.hashCode()
        }
    }

    /**
     * Unknown version
     */
    public class Unknown(override val text: String) : GradleDependencyVersion {

        override val isStatic: Boolean
            get() = false

        override fun contains(version: Static): Boolean = false

        override fun isUpdate(version: Static): Boolean = false

        override fun toString(): String = "Unknown"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as Unknown

            return text == other.text
        }

        override fun hashCode(): Int {
            return text.hashCode()
        }
    }
}

private val SEPARATORS = charArrayOf('.', '-', '_', '+')
private const val SNAPSHOT_SUFFIX = "-SNAPSHOT"

/**
 * Creates a [GradleDependencyVersion] from the given version text.
 *
 * @param versionText The version text to parse.
 * @return The corresponding [GradleDependencyVersion].
 */
public fun GradleDependencyVersion(versionText: String): GradleDependencyVersion = try {
    val trimmedText = versionText.trim()
    when {
        trimmedText.isEmpty() -> Unknown(versionText)

        trimmedText.length > 2
                && trimmedText.first() in Range.LOWER_BOUND_MARKERS
                && trimmedText.last() in Range.UPPER_BOUND_MARKERS -> Range(trimmedText)

        trimmedText.last() == '+' -> Prefix(trimmedText)

        trimmedText in GradleDependencyVersion.Latest.VALUES ->
            GradleDependencyVersion.Latest(trimmedText)

        trimmedText.endsWith(SNAPSHOT_SUFFIX) -> Snapshot(trimmedText)

        else -> Exact(trimmedText)
    }
} catch (ignored: IllegalArgumentException) {
    Unknown(versionText)
}

internal class GradleDependencyVersionSerializer : KSerializer<GradleDependencyVersion> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("GradleDependencyVersion", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): GradleDependencyVersion {
        return GradleDependencyVersion(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: GradleDependencyVersion) {
        encoder.encodeString(value.text)
    }
}

internal class GradleDependencyVersionStaticSerializer :
    KSerializer<GradleDependencyVersion.Static> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("GradleDependencyVersion.Static", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): GradleDependencyVersion.Static {
        return GradleDependencyVersion(decoder.decodeString()) as GradleDependencyVersion.Static
    }

    override fun serialize(encoder: Encoder, value: GradleDependencyVersion.Static) {
        encoder.encodeString(value.text)
    }
}