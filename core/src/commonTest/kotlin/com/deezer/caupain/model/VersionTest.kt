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
import kotlin.test.assertNull

class VersionTest {

    private fun Version.Resolved.checkUpdate(
        newVersion: String,
        shouldUpdate: Boolean
    ) {
        assertEquals(
            expected = shouldUpdate,
            actual = isUpdate(GradleDependencyVersion.Exact(newVersion))
        )
    }

    @Test
    fun testRichUpdate() {
        with(
            Version.Rich(
                require = GradleDependencyVersion.Exact("1.5")
            )
        ) {
            checkUpdate("1.4", false)
            checkUpdate("2.4", true)
        }
        with(
            Version.Rich(
                require = GradleDependencyVersion("[1.0, 2.0["),
                prefer = GradleDependencyVersion("1.5")
            )
        ) {
            checkUpdate("1.4", false)
            checkUpdate("1.6", true)
            checkUpdate("2.4", true)
        }
        with(
            Version.Rich(
                strictly = GradleDependencyVersion("[1.0, 2.0["),
                prefer = GradleDependencyVersion("1.5")
            )
        ) {
            checkUpdate("1.4", false)
            checkUpdate("1.6", true)
            checkUpdate("2.4", true)
        }
        with(
            Version.Rich(
                strictly = GradleDependencyVersion("[1.0, 2.0["),
                prefer = GradleDependencyVersion("1.5"),
                reject = GradleDependencyVersion("1.8")
            )
        ) {
            checkUpdate("1.4", false)
            checkUpdate("1.6", true)
            checkUpdate("1.8", false)
            checkUpdate("2.4", true)
        }
        with(
            Version.Rich(
                prefer = GradleDependencyVersion("1.5")
            )
        ) {
            checkUpdate("1.4", false)
            checkUpdate("2.4", true)
        }
        with(
            Version.Rich(
                prefer = GradleDependencyVersion("latest.release")
            )
        ) {
            checkUpdate("1.4", false)
            checkUpdate("1.6", false)
        }
        with(
            Version.Rich(
                require = GradleDependencyVersion("latest.release")
            )
        ) {
            checkUpdate("1.4", false)
            checkUpdate("1.6", false)
        }
        with(
            Version.Rich(
                strictly = GradleDependencyVersion("1.5")
            )
        ) {
            checkUpdate("1.4", false)
            checkUpdate("1.6", true)
        }
        with(
            Version.Rich(
                strictly = GradleDependencyVersion("[1.5,1.6[")
            )
        ) {
            checkUpdate("1.5.2", false)
            checkUpdate("1.6", true)
            checkUpdate("2.4", true)
        }
    }

    @Test
    fun testSimpleUpdate() {
        with(Version.Simple(GradleDependencyVersion("1.5"))) {
            checkUpdate("1.5", false)
            checkUpdate("1.6", true)
        }
        with(Version.Simple(GradleDependencyVersion("[1.0, 2.0["))) {
            checkUpdate("1.5", false)
            checkUpdate("1.6", false)
            checkUpdate("2.4", true)
        }
    }

    @Test
    fun testReference() {
        val resolvedVersion = Version.Simple(GradleDependencyVersion("1.5"))
        val key = "dep"
        val references = mapOf(key to resolvedVersion)
        assertEquals(
            expected = resolvedVersion,
            actual = Version.Reference(key).resolve(references)
        )
        assertNull(Version.Reference("otherKey").resolve(references))
    }
}