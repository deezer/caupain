package com.deezer.caupain.model

import com.deezer.caupain.model.versionCatalog.Version
import okio.Path

/**
 * Policy for selecting if a version can be used as an update for a dependency.
 */
public interface Policy {
    /**
     * The name of the policy.
     */
    public val name: String

    /**
     * Selects if the updated version can be used as an update for the current version.
     */
    public fun select(
        currentVersion: Version.Resolved,
        updatedVersion: GradleDependencyVersion.Single
    ): Boolean
}

internal expect object PolicyLoader {
    fun loadPolicies(paths: Iterable<Path>): Iterable<Policy>
}

internal val DEFAULT_POLICIES by lazy {
    buildMap {
        putPolicy(AndroidXVersionLevelPolicy)
    }
}

internal fun MutableMap<String, Policy>.putPolicy(policy: Policy) {
    put(policy.name, policy)
}

/**
 * This a default implementation for policy, based on the version levels used in AndroidX. Basically,
 * this version accept an update if the update's version level (alpha/beta/rc/stable) is greater than
 * or equal to the current version level.
 */
public object AndroidXVersionLevelPolicy : Policy {
    override val name: String = "androidx-version-level"

    override fun select(
        currentVersion: Version.Resolved,
        updatedVersion: GradleDependencyVersion.Single
    ): Boolean {
        val resolvedCurrentVersion = when (currentVersion) {
            is Version.Simple -> currentVersion.value as? GradleDependencyVersion.Single
            is Version.Rich -> currentVersion.probableSelectedVersion
        }
        if (updatedVersion is GradleDependencyVersion.Snapshot) {
            // We don't want to select snapshot versions if current is not snapshot
            return resolvedCurrentVersion is GradleDependencyVersion.Snapshot
        }
        val currentVersionLevel = resolvedCurrentVersion?.let(VersionLevel::of)
        // If no version level can be found we'll select the update
        return currentVersionLevel == null ||
                currentVersionLevel >= VersionLevel.of(updatedVersion)
    }

    private enum class VersionLevel(private val regex: Regex? = null) :
        Comparable<VersionLevel> {
        STABLE("^[0-9,.v-]+(-r)?$".toRegex()) {
            override fun matches(version: String): Boolean {
                return STABLE_KEYWORDS.any { keyword ->
                    version.uppercase().contains(keyword)
                }
                        || super.matches(version)
            }
        },
        RELEASE_CANDIDATE("^.*-rc[0-9]+?$".toRegex()),
        BETA("^.*-beta[0-9]+?$".toRegex()),
        ALPHA("^.*-alpha[0-9]+?$".toRegex()),
        OTHER {
            override fun matches(version: String) = true
        };

        protected open fun matches(version: String) = regex?.matches(version) == true

        companion object {
            private val STABLE_KEYWORDS = arrayOf("RELEASE", "FINAL", "GA")

            fun of(version: GradleDependencyVersion.Single) =
                entries.first { it.matches(version.exactVersion.text) }
        }
    }
}