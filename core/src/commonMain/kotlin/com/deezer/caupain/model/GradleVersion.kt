package com.deezer.caupain.model

import kotlinx.serialization.Serializable

/**
 * Gradle wrapper version
 */
@Serializable
public class GradleVersion(public val version: String) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as GradleVersion

        return version == other.version
    }

    override fun hashCode(): Int {
        return version.hashCode()
    }

    override fun toString(): String {
        return "GradleVersion(version=$version)"
    }
}