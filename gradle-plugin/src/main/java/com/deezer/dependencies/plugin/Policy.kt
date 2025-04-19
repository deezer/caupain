package com.deezer.dependencies.plugin

import com.deezer.dependencies.model.GradleDependencyVersion
import com.deezer.dependencies.model.Policy
import com.deezer.dependencies.model.versionCatalog.Version
import org.gradle.api.HasImplicitReceiver

data class VersionUpdateInfo(
    val currentVersion: Version.Resolved,
    val updatedVersion: GradleDependencyVersion.Single
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

    companion object {
        /**
         * Creates a policy from the given [Policy] instance.
         */
        fun from(policy: Policy) = Policy { policy.select(currentVersion, updatedVersion) }
    }
}