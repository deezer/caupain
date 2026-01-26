package com.deezer.caupain.model

import com.deezer.caupain.model.versionCatalog.Version

internal data class DependencyUpdateResult(
    val dependencyKey: String,
    val dependency: Dependency,
    val repository: Repository,
    val currentVersion: Version.Resolved,
    val updatedVersion: GradleDependencyVersion.Static,
)
