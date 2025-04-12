package com.deezer.dependencies.model

import com.deezer.dependencies.model.GradleDependencyVersion.Exact
import com.deezer.dependencies.model.GradleDependencyVersion.Prefix
import com.deezer.dependencies.model.GradleDependencyVersion.Range
import com.deezer.dependencies.model.GradleDependencyVersion.Snapshot
import com.deezer.dependencies.model.GradleDependencyVersion.Unknown
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.jvm.JvmInline

sealed interface GradleDependencyVersion {

    val text: String

    sealed interface Single : GradleDependencyVersion, Comparable<Single> {
        val exactVersion: Exact
    }

    operator fun contains(version: Single): Boolean

    fun isUpdate(version: Single): Boolean

    // Dependency version compared like in https://docs.gradle.org/current/userguide/single_versions.html#version_ordering
    @JvmInline
    value class Exact(private val version: String) : Single {

        override val text: String
            get() = version

        override val exactVersion: Exact
            get() = this

        override fun contains(version: Single): Boolean = this == version

        override fun compareTo(other: Single): Int = when (other) {
            is Exact -> compareTo(other)
            is Snapshot -> {
                val compareResult = compareTo(other.exactVersion)
                if (compareResult == 0) -1 else compareResult
            }
        }

        fun compareTo(other: Exact): Int {
            val parts = toParts()
            val otherParts = other.toParts()
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

        override fun isUpdate(version: Single): Boolean = when (version) {
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

        override fun toString() = version

        private sealed interface Part : Comparable<Part> {

            @JvmInline
            value class Numeric(val value: Int) : Part {
                override fun compareTo(other: Part): Int {
                    return when (other) {
                        is Numeric -> value.compareTo(other.value)
                        is Alphabetical -> 1
                    }
                }
            }

            @JvmInline
            value class Alphabetical(val value: String) : Part {
                override fun compareTo(other: Part): Int {
                    return when (other) {
                        is Numeric -> -1
                        is Alphabetical -> when {
                            value == other.value -> 0
                            value == DEV -> -1
                            other.value == DEV -> 1
                            else -> {
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
                    Type.NUMERIC -> Numeric(value.toInt())
                    Type.ALPHABETICAL -> Alphabetical(value)
                }
            }
        }
    }

    data class Range(override val text: String) : GradleDependencyVersion {

        private val lowerBound: Exact?
        private val upperBound: Exact?
        private val isLowerBoundExclusive: Boolean
        private val isUpperBoundExclusive: Boolean

        init {
            require(text.length > 2 && text.first() in LOWER_BOUND_MARKERS && text.last() in UPPER_BOUND_MARKERS) {
                "Wrong format for range $text"
            }
            isLowerBoundExclusive = text.first() in EXCLUSIVE_LOWER_BOUND_MARKERS
            isUpperBoundExclusive = text.last() in EXCLUSIVE_UPPER_BOUND_MARKERS
            val parts = text
                .substring(1, text.lastIndex)
                .split(',')
                .map { it.trim() }
            val lowerBoundText = parts.getOrNull(0)
            lowerBound = if (lowerBoundText.isNullOrBlank()) null else Exact(lowerBoundText)
            val upperBoundText = parts.getOrNull(1)
            upperBound = if (upperBoundText.isNullOrBlank()) null else Exact(upperBoundText)
        }

        override fun contains(version: Single): Boolean {
            val exactVersion = version.exactVersion
            return when {
                lowerBound == null && upperBound == null -> false

                lowerBound != null && exactVersion < lowerBound -> false

                upperBound != null && exactVersion > upperBound -> false

                exactVersion == lowerBound -> !isLowerBoundExclusive

                exactVersion == upperBound -> !isUpperBoundExclusive

                lowerBound == null && upperBound != null -> if (isUpperBoundExclusive) {
                    exactVersion < upperBound
                } else {
                    exactVersion <= upperBound
                }

                upperBound == null && lowerBound != null -> if (isLowerBoundExclusive) {
                    exactVersion > lowerBound
                } else {
                    exactVersion >= lowerBound
                }

                upperBound != null && lowerBound != null -> when {
                    isLowerBoundExclusive && isUpperBoundExclusive ->
                        exactVersion > lowerBound && exactVersion < upperBound

                    isLowerBoundExclusive -> exactVersion > lowerBound && exactVersion <= upperBound

                    isUpperBoundExclusive -> exactVersion >= lowerBound && exactVersion < upperBound

                    else -> exactVersion >= lowerBound && exactVersion <= upperBound
                }

                else -> false
            }
        }

        override fun isUpdate(version: Single): Boolean {
            return !contains(version) && upperBound?.isUpdate(version) == true
        }

        override fun toString(): String = text

        companion object {
            val LOWER_BOUND_MARKERS = charArrayOf('[', '(', ']')
            val UPPER_BOUND_MARKERS = charArrayOf(']', ')', '[')
            private val EXCLUSIVE_UPPER_BOUND_MARKERS = charArrayOf(')', '[')
            private val EXCLUSIVE_LOWER_BOUND_MARKERS = charArrayOf('(', ']')
        }
    }

    data class Prefix(override val text: String) : GradleDependencyVersion {

        private val regex: Regex

        private val version: Exact

        init {
            require(text.lastOrNull() == '+' && text.indexOfAny(SEPARATORS) >= 0) {
                "Wrong format for prefix $text"
            }
            regex = Regex("${Regex.escape(text.substring(0, text.lastIndex))}.*")
            val lastSeparatorIndex = text.lastIndexOfAny(SEPARATORS)
            version = Exact(text.substring(0, lastSeparatorIndex))
        }

        override fun contains(version: Single): Boolean {
            return regex.matches(version.text)
        }

        override fun isUpdate(version: Single): Boolean {
            return !contains(version) && this.version.isUpdate(version)
        }

        override fun toString(): String = text
    }

    data class Snapshot(override val text: String) : Single {

        override val exactVersion = Exact(text.dropLast(SNAPSHOT_SUFFIX.length))

        override fun compareTo(other: Single): Int = when (other) {
            is Snapshot -> exactVersion.compareTo(other.exactVersion)
            is Exact -> {
                val compareResult = exactVersion.compareTo(other)
                if (compareResult == 0) 1 else compareResult
            }
        }

        override fun contains(version: Single): Boolean = this == version

        override fun isUpdate(version: Single): Boolean = version.exactVersion > exactVersion

        override fun toString(): String = text
    }

    data class Unknown(override val text: String) : GradleDependencyVersion {
        override fun contains(version: Single): Boolean = false

        override fun isUpdate(version: Single): Boolean = false

        override fun toString(): String = "Unknown"
    }
}

private val SEPARATORS = charArrayOf('.', '-', '_', '+')
private const val SNAPSHOT_SUFFIX = "-SNAPSHOT"

fun GradleDependencyVersion(versionText: String): GradleDependencyVersion = when {
    versionText.isBlank() -> Unknown(versionText)

    versionText.length > 2
        && versionText.first() in Range.LOWER_BOUND_MARKERS
        && versionText.last() in Range.UPPER_BOUND_MARKERS -> Range(versionText)

    versionText.last() == '+' && versionText.indexOfAny(SEPARATORS) >= 0 ->
        Prefix(versionText)

    versionText.endsWith(SNAPSHOT_SUFFIX) -> Snapshot(versionText)

    else -> Exact(versionText)
}

class GradleDependencyVersionSerializer : KSerializer<GradleDependencyVersion> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("GradleDependencyVersion", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): GradleDependencyVersion {
        return GradleDependencyVersion(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: GradleDependencyVersion) {
        encoder.encodeString(value.text)
    }
}