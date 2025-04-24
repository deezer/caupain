package com.deezer.caupain.plugin

import com.deezer.caupain.model.GradleDependencyVersion
import com.deezer.caupain.model.Policy
import com.deezer.caupain.model.versionCatalog.Version
import org.gradle.api.HasImplicitReceiver

data class VersionUpdateInfo(
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

    companion object {
        /**
         * Creates a policy from the given [Policy] instance.
         */
        fun from(policy: Policy) = Policy { policy.select(currentVersion, updatedVersion) }
    }
}