package com.deezer.dependencies.model

import com.deezer.dependencies.Serializable

/**
 * Maven repository
 *
 * @property url The URL of the repository.
 * @property user The username for authentication (optional).
 * @property password The password for authentication (optional).
 */
public data class Repository(
    val url: String,
    val user: String?,
    val password: String?
) : Serializable {
    public constructor(url: String) : this(url, null, null)

    public companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Default repositories for dependency resolution.
 */
public object DefaultRepositories {
    /**
     * Represents the Google Maven repository.
     */
    public val google: Repository = Repository("https://dl.google.com/dl/android/maven2")

    /**
     * Represents the Maven Central repository.
     */
    public val mavenCentral: Repository = Repository("https://repo.maven.apache.org/maven2")

    /**
     * Represents the Gradle Plugin repository.
     */
    public val gradlePlugins: Repository = Repository("https://plugins.gradle.org/m2")
}
