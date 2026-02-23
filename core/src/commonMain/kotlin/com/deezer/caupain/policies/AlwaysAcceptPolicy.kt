package com.deezer.caupain.policies

import com.deezer.caupain.model.Dependency
import com.deezer.caupain.model.GradleDependencyVersion
import com.deezer.caupain.model.Policy
import com.deezer.caupain.model.versionCatalog.Version

/**
 * This is a default policy implementation that always accepts all updates.
 */
public object AlwaysAcceptPolicy : Policy {
    override val name: String = "always"

    override val description: String = "Policy that always accepts an update."

    override fun select(
        dependency: Dependency,
        currentVersion: Version.Resolved,
        updatedVersion: GradleDependencyVersion.Static
    ): Boolean = true
}
