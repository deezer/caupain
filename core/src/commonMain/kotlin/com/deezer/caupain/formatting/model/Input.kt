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
import dev.drewhamilton.poko.Poko
import kotlinx.serialization.Serializable

/**
 * Formatter input
 */
@Serializable
@Poko
public class Input(
    public val gradleUpdateInfo: GradleUpdateInfo?,
    public val updateInfos: Map<UpdateInfo.Type, List<UpdateInfo>>,
    public val ignoredUpdateInfos: List<UpdateInfo>,
    public val versionReferenceInfo: List<VersionReferenceInfo>?,
    public val selfUpdateInfo: SelfUpdateInfo?
) {
    /**
     * Returns true if the input is empty, meaning there are no updates to display.
     */
    public val isEmpty: Boolean
        get() = (updateInfos.isEmpty() || updateInfos.values.all { it.isEmpty() })
                && ignoredUpdateInfos.isEmpty()
                && versionReferenceInfo.isNullOrEmpty()
                && gradleUpdateInfo == null
                && selfUpdateInfo == null

    public constructor(
        updateResult: DependenciesUpdateResult,
        showVersionReferences: Boolean
    ) : this(
        gradleUpdateInfo = updateResult.gradleUpdateInfo,
        updateInfos = updateResult.updateInfos,
        ignoredUpdateInfos = updateResult.ignoredUpdateInfos,
        selfUpdateInfo = updateResult.selfUpdateInfo,
        versionReferenceInfo = if (showVersionReferences && updateResult.versionCatalog != null) {
            computeVersionReferenceInfos(updateResult.versionCatalog, updateResult.updateInfos)
        } else {
            null
        }
    )
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
@Poko
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
}