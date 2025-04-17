package com.deezer.dependencies.plugin

import com.deezer.dependencies.model.GradleDependencyVersion
import com.deezer.dependencies.model.versionCatalog.Version
import org.gradle.api.HasImplicitReceiver

data class VersionUpdateInfo(
    val currentVersion: Version.Direct,
    val updatedVersion: GradleDependencyVersion.Single
)

@HasImplicitReceiver
fun interface Policy {
    fun select(updateInfo: VersionUpdateInfo): Boolean
}