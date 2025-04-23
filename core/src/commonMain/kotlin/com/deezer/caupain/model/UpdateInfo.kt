package com.deezer.caupain.model

/**
 * UpdateInfo is a data class that holds information about a dependency update.
 *
 * @property dependency The key of the dependency in the version catalog.
 * @property dependencyId The ID of the dependency.
 * @property name The detailed name of the dependency
 * @property url The URL of the dependency.
 * @property currentVersion The current version of the dependency.
 * @property updatedVersion The updated version of the dependency.
 */
public class UpdateInfo(
    public val dependency: String,
    public val dependencyId: String,
    public val name: String? = null,
    public val url: String? = null,
    public val currentVersion: String,
    public val updatedVersion: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as UpdateInfo

        if (dependency != other.dependency) return false
        if (dependencyId != other.dependencyId) return false
        if (name != other.name) return false
        if (url != other.url) return false
        if (currentVersion != other.currentVersion) return false
        if (updatedVersion != other.updatedVersion) return false

        return true
    }

    override fun hashCode(): Int {
        var result = dependency.hashCode()
        result = 31 * result + dependencyId.hashCode()
        result = 31 * result + (name?.hashCode() ?: 0)
        result = 31 * result + (url?.hashCode() ?: 0)
        result = 31 * result + currentVersion.hashCode()
        result = 31 * result + updatedVersion.hashCode()
        return result
    }

    override fun toString(): String {
        return "UpdateInfo(dependency='$dependency', dependencyId='$dependencyId', name=$name, url=$url, currentVersion='$currentVersion', updatedVersion='$updatedVersion')"
    }

    public enum class Type(public val title: String) {
        LIBRARY("Libraries"), PLUGIN("Plugins")
    }
}

/**
 * Holds information about a Gradle update.
 *
 * @property url The URL of the dependency.
 * @property currentVersion The current Gradle version.
 * @property updatedVersion The updated Gradle version
 */
public class GradleUpdateInfo(
    public val currentVersion: String,
    public val updatedVersion: String
) {
    public val url: String = "https://docs.gradle.org/$updatedVersion/release-notes.html"
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as GradleUpdateInfo

        if (currentVersion != other.currentVersion) return false
        if (updatedVersion != other.updatedVersion) return false

        return true
    }

    override fun hashCode(): Int {
        var result = currentVersion.hashCode()
        result = 31 * result + updatedVersion.hashCode()
        return result
    }

    override fun toString(): String {
        return "GradleUpdateInfo(currentVersion='$currentVersion', updatedVersion='$updatedVersion')"
    }
}
