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
import dev.drewhamilton.poko.Poko

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
 * @property releaseNoteUrl The URL to the release notes of the dependency.
 * @property currentVersion The current version of the dependency.
 * @property updatedVersion The updated version of the dependency.
 */
@Serializable
@Poko
public class UpdateInfo(
    public val dependency: String,
    public val dependencyId: String,
    public val name: String? = null,
    public val url: String? = null,
    public val releaseNoteUrl: String? = null,
    public val currentVersion: Version.Resolved,
    public val updatedVersion: GradleDependencyVersion.Static
) {
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
@Poko
public class GradleUpdateInfo(
    public val currentVersion: String,
    public val updatedVersion: String
) {
    @Transient
    public val url: String = "https://docs.gradle.org/$updatedVersion/release-notes.html"
}

/**
 * Info about the Caupain self-update.
 *
 * @property currentVersion The current version of Caupain.
 * @property updatedVersion The version to which Caupain can be updated.
 * @param sources The sources from which the update can be fetched.
 */
@Serializable
@Poko
public class SelfUpdateInfo(
    public val currentVersion: String,
    public val updatedVersion: String,
    public val sources: List<Source>
) {
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
