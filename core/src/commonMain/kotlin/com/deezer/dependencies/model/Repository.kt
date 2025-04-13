package com.deezer.dependencies.model

public data class Repository(
    val url: String,
    val user: String?,
    val password: String?
) {
    public constructor(url: String) : this(url, null, null)
}

public object DefaultRepositories {
    public val google: Repository = Repository("https://dl.google.com/dl/android/maven2")
    public val mavenCentral: Repository = Repository("https://repo.maven.apache.org/maven2")
    public val gradlePlugins: Repository = Repository("https://plugins.gradle.org/m2")
}
