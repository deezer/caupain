/*
 * MIT License
 *
 * Copyright (c) 2026 Deezer
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
 *
 */

package com.deezer.caupain.policies

import com.deezer.caupain.model.Dependency
import com.deezer.caupain.model.GradleDependencyVersion
import com.deezer.caupain.model.Policy
import com.deezer.caupain.model.group
import com.deezer.caupain.model.name
import com.deezer.caupain.model.versionCatalog.Version

/**
 * This is a policy implementation that rejects Guava updates with the prefix "-jre" when the current
 * version uses "-android" prefix. This is needed because alphabetically, "-jre" is greater than "-android",
 * but in practice, the "-jre" versions are not compatible with Android and should not be selected
 * as updates for dependencies using the "-android" prefix.
 */
public object GuavaAndroidPolicy : Policy {

    override val name: String
        get() = "guava-android"

    override val description: String
        get() = "Policy that rejects Guava updates with the prefix \"-jre\" when the current " +
                "version uses \"-android\" prefix. This is needed because alphabetically, \"-jre\" " +
                "is greater than \"-android\", but in practice, the \"-jre\" versions are not " +
                "compatible with Android and should not be selected as updates for dependencies " +
                "using the \"-android\" prefix"

    override fun select(
        dependency: Dependency,
        currentVersion: Version.Resolved,
        updatedVersion: GradleDependencyVersion.Static
    ): Boolean {
        // We only want to apply this policy for Guava library
        if (dependency.group != GUAVA_GROUP || dependency.name != GUAVA_ARTIFACT) return true

        val resolvedCurrentVersionString = when (currentVersion) {
            is Version.Simple -> currentVersion.value as? GradleDependencyVersion.Static
            is Version.Rich -> currentVersion.probableSelectedVersion
        }?.exactVersion?.toString() ?: return true
        val updatedVersionString = updatedVersion.exactVersion.toString()

        return !resolvedCurrentVersionString.endsWith("-android")
                || !updatedVersionString.endsWith("-jre")
    }

    private const val GUAVA_GROUP = "com.google.guava"
    private const val GUAVA_ARTIFACT = "guava"
}
