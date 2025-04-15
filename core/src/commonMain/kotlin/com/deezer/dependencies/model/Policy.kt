@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.deezer.dependencies.model

import com.deezer.dependencies.model.versionCatalog.Version

public interface Policy {
    public val name: String

    public fun select(
        currentVersion: Version.Direct,
        updatedVersion: GradleDependencyVersion.Single
    ): Boolean
}

internal expect object PolicyLoader {
    fun loadPolicies(): Iterable<Policy>
}

internal val ALL_POLICIES by lazy {
    buildMap {
        putPolicy(AndroidXVersionLevelPolicy)
        PolicyLoader.loadPolicies().associateByTo(this) { it.name }
    }
}

private fun MutableMap<String, Policy>.putPolicy(policy: Policy) {
    put(policy.name, policy)
}

internal object AndroidXVersionLevelPolicy : Policy {
    override val name = "androidx-version-level"

    override fun select(
        currentVersion: Version.Direct,
        updatedVersion: GradleDependencyVersion.Single
    ): Boolean {
        val resolvedCurrentVersion = when (currentVersion) {
            is Version.Simple -> currentVersion.value as? GradleDependencyVersion.Single
            is Version.Rich -> currentVersion.probableSelectedVersion
        }
        val currentVersionLevel = resolvedCurrentVersion?.let(VersionLevel::of)
        // If no version level can be found we'll select the update
        return currentVersionLevel == null ||
                currentVersionLevel <= VersionLevel.of(updatedVersion)
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