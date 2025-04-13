package com.deezer.dependencies.model

public data class UpdateInfo(
    val dependency: String,
    val dependencyId: String,
    val name: String? = null,
    val url: String? = null,
    val updatedVersion: String
)
