@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.deezer.dependencies.model

import com.deezer.dependencies.model.versionCatalog.Version

public interface Policy {
    public val name: String

    public fun select(
        currentVersion: Version.Direct,
        updatedVersion: GradleDependencyVersion.Single
    ): Boolean
}

internal expect object PolicyLoader {
    fun loadPolicies(): Iterable<Policy>
}

internal val ALL_POLICIES = buildMap {
    // TODO: add default policies
    PolicyLoader.loadPolicies().associateByTo(this) { it.name }
}