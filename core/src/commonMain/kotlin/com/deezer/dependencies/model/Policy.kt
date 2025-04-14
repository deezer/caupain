package com.deezer.dependencies.model

import com.deezer.dependencies.model.versionCatalog.Version

public interface Policy {
    public val name: String

    public fun select(
        currentVersion: Version.Direct,
        updatedVersion: GradleDependencyVersion.Single
    ): Boolean
}

internal val ALL_POLICIES = buildMap {
    // TODO: add default policies
    loadExternalPolicies().associateByTo(this) { it.name }
}

internal expect fun loadExternalPolicies(): Iterable<Policy>