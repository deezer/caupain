package com.deezer.dependencies.model

data class UpdateInfo(
    val dependency: Dependency,
    val name: String? = null,
    val url: String? = null,
    val updatedVersion: GradleDependencyVersion.Single
)
