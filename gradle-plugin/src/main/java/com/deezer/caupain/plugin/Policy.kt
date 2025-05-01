package com.deezer.caupain.plugin

import com.deezer.caupain.model.Dependency
import com.deezer.caupain.model.GradleDependencyVersion
import com.deezer.caupain.model.versionCatalog.Version
import org.gradle.api.HasImplicitReceiver

data class VersionUpdateInfo(
    val dependency: Dependency,
    val currentVersion: Version.Resolved,
    val updatedVersion: GradleDependencyVersion.Static
)

/**
 * Policy interface for selecting which dependencies to update.
 */
@HasImplicitReceiver
fun interface Policy {

    /**
     * Selects whether to update the dependency based on the current and updated version.
     *
     * @return true if the update can be selected, false otherwise.
     */
    fun select(updateInfo: VersionUpdateInfo): Boolean
}