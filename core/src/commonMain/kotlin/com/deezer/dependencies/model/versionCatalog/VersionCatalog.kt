package com.deezer.dependencies.model.versionCatalog

import com.deezer.dependencies.model.Dependency
import kotlinx.serialization.Serializable

@Serializable
internal data class VersionCatalog(
    val versions: Map<String, Version.Direct> = emptyMap(),
    val libraries: Map<String, Dependency.Library> = emptyMap(),
    val plugins: Map<String, Dependency.Plugin> = emptyMap()
) {
    val dependencies: Map<String, Dependency>
        get() = libraries + plugins
}
