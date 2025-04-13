package com.deezer.dependencies.model

import okio.Path
import okio.Path.Companion.toPath

public data class Configuration(
    val repositories: List<Repository> = listOf(
        DefaultRepositories.mavenCentral,
        DefaultRepositories.google,
    ),
    val pluginRepositories: List<Repository> = listOf(
        DefaultRepositories.gradlePlugins,
        DefaultRepositories.mavenCentral,
        DefaultRepositories.google,
    ),
    val versionCatalogPath: Path = "gradle/libs.versions.toml".toPath(),
    val excludedDependencies: Set<String> = emptySet()
)