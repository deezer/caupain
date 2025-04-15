package com.deezer.dependencies.model

public data class UpdateInfo(
    val dependency: String,
    val type: Type,
    val dependencyId: String,
    val name: String? = null,
    val url: String? = null,
    val currentVersion: String,
    val updatedVersion: String
) {
    public enum class Type(public val title: String) {
        PLUGIN("Plugins"), LIBRARY("Libraries")
    }
}
