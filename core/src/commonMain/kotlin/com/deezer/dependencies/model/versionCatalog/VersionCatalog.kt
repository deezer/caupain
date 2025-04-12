@file:UseSerializers(VersionSerializer::class)

package com.deezer.dependencies.model.versionCatalog

import com.deezer.dependencies.model.Dependency
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

@Serializable
data class VersionCatalog(
    val versions: Map<String, Version> = emptyMap(),
    val libraries: Map<String, Dependency.Library> = emptyMap(),
    val plugins: Map<String, Dependency.Plugin> = emptyMap()
) {
    val dependencies: Map<String, Dependency>
        get() = libraries + plugins
}
