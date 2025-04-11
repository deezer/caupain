package com.deezer.dependencies.model

data class Repository(
    val url: String,
    val user: String?,
    val password: String?
) {
    constructor(url: String) : this(url, null, null)
}

object DefaultRepositories {
    val google = Repository("https://dl.google.com/dl/android/maven2/")
    val mavenCentral = Repository("https://repo.maven.apache.org/maven2/")
    val gradlePlugins = Repository("https://plugins.gradle.org/m2")
}
