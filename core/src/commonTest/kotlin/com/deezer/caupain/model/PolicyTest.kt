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
import kotlin.test.Test
import kotlin.test.assertEquals

class PolicyTest {

    private fun Policy.testSelect(
        currentVersion: String,
        updatedVersion: String,
        expected: Boolean
    ) {
        assertEquals(
            expected = expected,
            actual = select(
                dependency = Dependency.Library("com.example", "example"),
                currentVersion = Version.Simple(GradleDependencyVersion(currentVersion)),
                updatedVersion = GradleDependencyVersion(updatedVersion) as GradleDependencyVersion.Static
            )
        )
    }

    @Test
    fun testVersionLevelPolicy() {
        StabilityLevelPolicy.testSelect(
            currentVersion = "1.0.0",
            updatedVersion = "1.0.1",
            expected = true
        )
        StabilityLevelPolicy.testSelect(
            currentVersion = "1.0.0-beta01",
            updatedVersion = "1.0.0",
            expected = true
        )
        StabilityLevelPolicy.testSelect(
            currentVersion = "1.0.0-beta01",
            updatedVersion = "1.0.1-alpha01",
            expected = false
        )
        StabilityLevelPolicy.testSelect(
            currentVersion = "1.0.0",
            updatedVersion = "1.0.1-SNAPSHOT",
            expected = false
        )
        StabilityLevelPolicy.testSelect(
            currentVersion = "1.0.0-SNAPSHOT",
            updatedVersion = "1.0.1-SNAPSHOT",
            expected = true
        )
    }
}