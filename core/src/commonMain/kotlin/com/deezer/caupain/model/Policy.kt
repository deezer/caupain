/*
 * MIT License
 *
 * Copyright (c) 2025 Deezer
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

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
     * The description of the policy. Default returns null.
     */
    public val description: String?
        get() = null

    /**
     * Selects if the updated version can be used as an update for the current version.
     */
    public fun select(
        dependency: Dependency,
        currentVersion: Version.Resolved,
        updatedVersion: GradleDependencyVersion.Static
    ): Boolean
}

internal expect fun loadPolicies(paths: Iterable<Path>, logger: Logger): Iterable<Policy>

internal val DEFAULT_POLICIES = listOf(
    StabilityLevelPolicy,
    AlwaysAcceptPolicy
)

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

/**
 * This is a default policy implementation that always accepts all updates.
 */
public object AlwaysAcceptPolicy : Policy {
    override val name: String = "always"

    override val description: String? = "Policy that always accepts an update."

    override fun select(
        dependency: Dependency,
        currentVersion: Version.Resolved,
        updatedVersion: GradleDependencyVersion.Static
    ): Boolean = true
}