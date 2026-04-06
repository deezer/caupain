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

package com.deezer.caupain.model

import com.deezer.caupain.model.versionCatalog.Version
import com.deezer.caupain.policies.FilterPolicy
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilterPolicyTest {

    @Test
    fun testFilterPolicy() {
        val filters = listOf(
            Filter.LibraryFilter(
                group = "com.example",
                name = "example",
                versionFilter = GradleDependencyVersion("1.+")
            ),
            Filter.LibraryFilter(
                group = "com.example.**",
                versionFilter = GradleDependencyVersion("1.+")
            ),
            Filter.PluginFilter(
                id = "com.example.plugin",
                versionFilter = GradleDependencyVersion("[1.2, 1.5]")
            )
        )
        val policy = FilterPolicy(filters)
        assertTrue(
            policy.select(
                dependency = Dependency.Library("com.example", "example"),
                currentVersion = Version.Simple(GradleDependencyVersion("0.0.1")),
                updatedVersion = GradleDependencyVersion("1.1.0") as GradleDependencyVersion.Static
            )
        )
        assertFalse(
            policy.select(
                dependency = Dependency.Library("com.example", "example"),
                currentVersion = Version.Simple(GradleDependencyVersion("0.0.1")),
                updatedVersion = GradleDependencyVersion("2.1.0") as GradleDependencyVersion.Static
            )
        )
        assertTrue(
            policy.select(
                dependency = Dependency.Library("com.example.fake", "other"),
                currentVersion = Version.Simple(GradleDependencyVersion("0.0.1")),
                updatedVersion = GradleDependencyVersion("1.1.0") as GradleDependencyVersion.Static
            )
        )
        assertFalse(
            policy.select(
                dependency = Dependency.Library("com.example.fake", "other"),
                currentVersion = Version.Simple(GradleDependencyVersion("0.0.1")),
                updatedVersion = GradleDependencyVersion("2.1.0") as GradleDependencyVersion.Static
            )
        )
        assertTrue(
            policy.select(
                dependency = Dependency.Plugin("com.example.plugin"),
                currentVersion = Version.Simple(GradleDependencyVersion("1.0.0")),
                updatedVersion = GradleDependencyVersion("1.3.0") as GradleDependencyVersion.Static
            )
        )
        assertFalse(
            policy.select(
                dependency = Dependency.Plugin("com.example.plugin"),
                currentVersion = Version.Simple(GradleDependencyVersion("1.0.0")),
                updatedVersion = GradleDependencyVersion("1.6.0") as GradleDependencyVersion.Static
            )
        )
        assertTrue(
            policy.select(
                dependency = Dependency.Library("com.other", "example"),
                currentVersion = Version.Simple(GradleDependencyVersion("1.0.1")),
                updatedVersion = GradleDependencyVersion("2.1.0") as GradleDependencyVersion.Static
            )
        )
        assertTrue(
            policy.select(
                dependency = Dependency.Plugin("com.other.plugin"),
                currentVersion = Version.Simple(GradleDependencyVersion("1.0.1")),
                updatedVersion = GradleDependencyVersion("1.6.0") as GradleDependencyVersion.Static
            )
        )
    }
}
