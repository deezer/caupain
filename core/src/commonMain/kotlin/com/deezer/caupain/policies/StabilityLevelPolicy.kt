package com.deezer.caupain.policies

import com.deezer.caupain.model.Dependency
import com.deezer.caupain.model.GradleDependencyVersion
import com.deezer.caupain.model.Policy
import com.deezer.caupain.model.versionCatalog.Version

/**
 * This is a default policy implementation based on stability levels. Basically, this version accepts
 * an update if the update's stability level (alpha/beta/rc/stable) is greater than or equal to the
 * current stability level.
 */
public object StabilityLevelPolicy : Policy {
    override val name: String = "stability-level"

    override val description: String = "Policy based on stability levels of versions. " +
            "It selects updates if the update's stability level is greater than or equal to the current version's stability level."

    override fun select(
        dependency: Dependency,
        currentVersion: Version.Resolved,
        updatedVersion: GradleDependencyVersion.Static
    ): Boolean {
        val resolvedCurrentVersion = when (currentVersion) {
            is Version.Simple -> currentVersion.value as? GradleDependencyVersion.Static
            is Version.Rich -> currentVersion.probableSelectedVersion
        }
        if (updatedVersion is GradleDependencyVersion.Snapshot) {
            // We don't want to select snapshot versions if current is not snapshot
            return resolvedCurrentVersion is GradleDependencyVersion.Snapshot
        }
        val currentLevel = resolvedCurrentVersion?.let(StabilityLevel::of)
        // If no version level can be found we'll select the update
        return currentLevel == null || currentLevel >= StabilityLevel.of(updatedVersion)
    }

    private enum class StabilityLevel(private val regex: Regex? = null) :
        Comparable<StabilityLevel> {
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

        open fun matches(version: String) = regex?.matches(version) == true

        companion object {
            private val STABLE_KEYWORDS = arrayOf("RELEASE", "FINAL", "GA")

            fun of(version: GradleDependencyVersion.Static) =
                entries.first { it.matches(version.exactVersion.text) }
        }
    }
}
