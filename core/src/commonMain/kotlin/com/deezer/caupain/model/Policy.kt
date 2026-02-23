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
import com.deezer.caupain.policies.AlwaysAcceptPolicy
import com.deezer.caupain.policies.GuavaAndroidPolicy
import com.deezer.caupain.policies.StabilityLevelPolicy
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
    AlwaysAcceptPolicy,
    GuavaAndroidPolicy,
)

@Deprecated("Use com.deezer.caupain.policies.StabilityLevelPolicy instead")
public typealias StabilityLevelPolicy = StabilityLevelPolicy

@Deprecated("Use com.deezer.caupain.policies.AlwaysAcceptPolicy instead")
public typealias AlwaysAcceptPolicy = AlwaysAcceptPolicy
