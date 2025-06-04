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

import com.deezer.caupain.model.versionCatalog.Version

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

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
@Serializable
public class UpdateInfo(
    public val dependency: String,
    public val dependencyId: String,
    public val name: String? = null,
    public val url: String? = null,
    public val currentVersion: Version.Resolved,
    public val updatedVersion: GradleDependencyVersion.Static
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

    /**
     * Update info type (library or plugin).
     */
    @Serializable
    public enum class Type(public val title: String) {
        @SerialName("libraries") LIBRARY("Libraries"),
        @SerialName("plugins") PLUGIN("Plugins")
    }
}

/**
 * Holds information about a Gradle update.
 *
 * @property currentVersion The current Gradle version.
 * @property updatedVersion The updated Gradle version
 */
@Serializable
public class GradleUpdateInfo(
    public val currentVersion: String,
    public val updatedVersion: String
) {
    @Transient
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

/**
 * Info about the Caupain self-update.
 *
 * @property currentVersion The current version of Caupain.
 * @property updatedVersion The version to which Caupain can be updated.
 * @param sources The sources from which the update can be fetched.
 */
@Serializable
public class SelfUpdateInfo(
    public val currentVersion: String,
    public val updatedVersion: String,
    public val sources: List<Source>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as SelfUpdateInfo

        if (currentVersion != other.currentVersion) return false
        if (updatedVersion != other.updatedVersion) return false
        if (sources != other.sources) return false

        return true
    }

    override fun hashCode(): Int {
        var result = currentVersion.hashCode()
        result = 31 * result + updatedVersion.hashCode()
        result = 31 * result + sources.hashCode()
        return result
    }

    override fun toString(): String {
        return "SelfUpdateInfo(currentVersion='$currentVersion', updatedVersion='$updatedVersion', sources=$sources)"
    }

    /**
     * Update source.
     */
    @Serializable
    public enum class Source(public val description: String, public val link: String? = null) {
        @SerialName("plugins") PLUGINS("plugins"),
        @SerialName("githubReleases") GITHUB_RELEASES("Github releases", "https://github.com/deezer/caupain/releases"),
        @SerialName("brew") BREW("Hombrew"),
        @SerialName("apt") APT("apt")
    }
}
