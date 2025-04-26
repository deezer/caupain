package com.deezer.caupain.model

import com.deezer.caupain.model.versionCatalog.Version

internal data class Ignores(
    val refs: Set<String> = emptySet(),
    val libraryKeys: Set<String> = emptySet(),
    val pluginKeys: Set<String> = emptySet()
)

internal fun Ignores.isExcluded(key: String, dependency: Dependency): Boolean {
    return when {
        dependency.version is Version.Reference ->
            (dependency.version as? Version.Reference)?.ref in refs

        dependency is Dependency.Library -> key in libraryKeys

        dependency is Dependency.Plugin -> key in pluginKeys

        else -> false
    }
}