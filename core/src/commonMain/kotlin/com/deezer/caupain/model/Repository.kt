package com.deezer.caupain.model

import com.deezer.caupain.Serializable

/**
 * Maven repository
 *
 * @property url The URL of the repository.
 * @property user The username for authentication (optional).
 * @property password The password for authentication (optional).
 */
public class Repository(
    public val url: String,
    public val user: String?,
    public val password: String?
) : Serializable {
    public constructor(url: String) : this(url, null, null)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Repository

        if (url != other.url) return false
        if (user != other.user) return false
        if (password != other.password) return false

        return true
    }

    override fun hashCode(): Int {
        var result = url.hashCode()
        result = 31 * result + (user?.hashCode() ?: 0)
        result = 31 * result + (password?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "Repository(user=$user, url='$url', password=$password)"
    }

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
