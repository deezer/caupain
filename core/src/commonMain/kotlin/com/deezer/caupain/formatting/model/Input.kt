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

package com.deezer.caupain.formatting.model

import com.deezer.caupain.model.DependenciesUpdateResult
import com.deezer.caupain.model.GradleDependencyVersion
import com.deezer.caupain.model.GradleUpdateInfo
import com.deezer.caupain.model.SelfUpdateInfo
import com.deezer.caupain.model.UpdateInfo
import com.deezer.caupain.model.versionCatalog.Version
import com.deezer.caupain.model.versionCatalog.VersionCatalog
import kotlinx.serialization.Serializable

/**
 * Formatter input
 */
@Serializable
public class Input(
    public val gradleUpdateInfo: GradleUpdateInfo?,
    public val updateInfos: Map<UpdateInfo.Type, List<UpdateInfo>>,
    public val versionReferenceInfo: List<VersionReferenceInfo>?,
    public val selfUpdateInfo: SelfUpdateInfo?
) {
    /**
     * Returns true if the input is empty, meaning there are no updates to display.
     */
    public val isEmpty: Boolean
        get() = updateInfos.isEmpty()
                && versionReferenceInfo.isNullOrEmpty()
                && gradleUpdateInfo == null
                && selfUpdateInfo == null

    public constructor(
        updateResult: DependenciesUpdateResult,
        showVersionReferences: Boolean
    ) : this(
        gradleUpdateInfo = updateResult.gradleUpdateInfo,
        updateInfos = updateResult.updateInfos,
        selfUpdateInfo = updateResult.selfUpdateInfo,
        versionReferenceInfo = if (showVersionReferences && updateResult.versionCatalog != null) {
            computeVersionReferenceInfos(updateResult.versionCatalog, updateResult.updateInfos)
        } else {
            null
        }
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Input

        if (gradleUpdateInfo != other.gradleUpdateInfo) return false
        if (updateInfos != other.updateInfos) return false
        if (versionReferenceInfo != other.versionReferenceInfo) return false
        if (selfUpdateInfo != other.selfUpdateInfo) return false

        return true
    }

    override fun hashCode(): Int {
        var result = gradleUpdateInfo?.hashCode() ?: 0
        result = 31 * result + updateInfos.hashCode()
        result = 31 * result + (versionReferenceInfo?.hashCode() ?: 0)
        result = 31 * result + (selfUpdateInfo?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "Input(gradleUpdateInfo=$gradleUpdateInfo, updateInfos=$updateInfos, versionReferenceInfo=$versionReferenceInfo, selfUpdateInfo=$selfUpdateInfo)"
    }
}

private data class SimpleDependencyInfo(val key: String, val type: Type) {
    enum class Type {
        LIBRARY, PLUGIN
    }
}

internal fun computeVersionReferenceInfos(
    versionCatalog: VersionCatalog,
    updateInfos: Map<UpdateInfo.Type, List<UpdateInfo>>
): List<VersionReferenceInfo> {
    // Associate version references with their dependencies
    val depsByVersionRef = buildMap {
        for ((key, dep) in versionCatalog.libraries) {
            if (dep.version is Version.Reference && dep.version.ref in versionCatalog.versions.keys) {
                getOrPut(dep.version.ref) { mutableListOf<SimpleDependencyInfo>() }.add(
                    SimpleDependencyInfo(key, SimpleDependencyInfo.Type.LIBRARY)
                )
            }
        }
        for ((key, dep) in versionCatalog.plugins) {
            if (dep.version is Version.Reference && dep.version.ref in versionCatalog.versions.keys) {
                getOrPut(dep.version.ref) { mutableListOf<SimpleDependencyInfo>() }.add(
                    SimpleDependencyInfo(key, SimpleDependencyInfo.Type.PLUGIN)
                )
            }
        }
    }
    // Check updates for each version reference
    return depsByVersionRef.mapNotNull { (ref, deps) ->
        val updatedLibs = updateInfos[UpdateInfo.Type.LIBRARY]
            ?.asSequence()
            .orEmpty()
            .filter { info ->
                deps.any { it.key == info.dependency && it.type == SimpleDependencyInfo.Type.LIBRARY }
            }
        val updatedPlugins = updateInfos[UpdateInfo.Type.PLUGIN]
            ?.asSequence()
            .orEmpty()
            .filter { info ->
                deps.any { it.key == info.dependency && it.type == SimpleDependencyInfo.Type.PLUGIN }
            }
        val updatedVersion = sequenceOf(updatedLibs, updatedPlugins)
            .flatten()
            .maxOfOrNull { it.updatedVersion }
            ?: return@mapNotNull null
        VersionReferenceInfo(
            id = ref,
            libraryKeys = deps
                .asSequence()
                .filter { it.type == SimpleDependencyInfo.Type.LIBRARY }
                .map { it.key }
                .toList(),
            updatedLibraries = updatedLibs
                .map { it.dependency to it.updatedVersion }
                .toMap(),
            pluginKeys = deps
                .asSequence()
                .filter { it.type == SimpleDependencyInfo.Type.PLUGIN }
                .map { it.key }
                .toList(),
            updatedPlugins = updatedPlugins
                .map { it.dependency to it.updatedVersion }
                .toMap(),
            currentVersion = requireNotNull(versionCatalog.versions[ref]),
            updatedVersion = updatedVersion
        )
    }
}

/**
 * Information about updates for a specific references in the versions block.
 */
@Serializable
public class VersionReferenceInfo(
    public val id: String,
    public val libraryKeys: List<String>,
    public val updatedLibraries: Map<String, GradleDependencyVersion.Static>,
    public val pluginKeys: List<String>,
    public val updatedPlugins: Map<String, GradleDependencyVersion.Static>,
    public val currentVersion: Version.Resolved,
    public val updatedVersion: GradleDependencyVersion.Static
) {
    internal val nbFullyUpdatedLibraries by lazy {
        updatedLibraries.count { it.value == updatedVersion }
    }

    internal val fullyUpdatedLibraries by lazy {
        updatedLibraries.filter { it.value == updatedVersion }.keys
    }

    internal val nbFullyUpdatedPlugins by lazy {
        updatedPlugins.count { it.value == updatedVersion }
    }

    internal val fullyUpdatedPlugins by lazy {
        updatedPlugins.filter { it.value == updatedVersion }.keys
    }

    internal val isFullyUpdated: Boolean
        get() = nbFullyUpdatedLibraries == libraryKeys.size && nbFullyUpdatedPlugins == pluginKeys.size

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as VersionReferenceInfo

        if (id != other.id) return false
        if (libraryKeys != other.libraryKeys) return false
        if (updatedLibraries != other.updatedLibraries) return false
        if (pluginKeys != other.pluginKeys) return false
        if (updatedPlugins != other.updatedPlugins) return false
        if (currentVersion != other.currentVersion) return false
        if (updatedVersion != other.updatedVersion) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + libraryKeys.hashCode()
        result = 31 * result + updatedLibraries.hashCode()
        result = 31 * result + pluginKeys.hashCode()
        result = 31 * result + updatedPlugins.hashCode()
        result = 31 * result + currentVersion.hashCode()
        result = 31 * result + updatedVersion.hashCode()
        return result
    }

    override fun toString(): String {
        return "VersionReferenceInfo(id='$id', libraryKeys=$libraryKeys, updatedLibraryKeys=$updatedLibraries, pluginKeys=$pluginKeys, updatedPluginKeys=$updatedPlugins, currentVersion=$currentVersion, updatedVersion=$updatedVersion)"
    }
}