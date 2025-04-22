package com.deezer.caupain.model.versionCatalog

import com.deezer.caupain.model.Dependency
import kotlinx.serialization.Serializable

@Serializable
internal data class VersionCatalog(
    val versions: Map<String, Version.Resolved> = emptyMap(),
    val libraries: Map<String, Dependency.Library> = emptyMap(),
    val plugins: Map<String, Dependency.Plugin> = emptyMap()
) {
    val dependencies: Map<String, Dependency>
        get() = libraries + plugins
}
