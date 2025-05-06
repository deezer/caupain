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

import kotlin.test.Test
import kotlin.test.assertEquals

class PrefixTest {

    private fun assertContains(
        prefixVersion: String,
        versionToCheck: String,
        expected: Boolean
    ) {
        val version = GradleDependencyVersion.Prefix(prefixVersion)
        val toCheck = GradleDependencyVersion(versionToCheck) as GradleDependencyVersion.Static
        assertEquals(expected, version.contains(toCheck))
    }

    private fun assertIsUpdate(
        prefixVersion: String,
        versionToCheck: String,
        expected: Boolean
    ) {
        val version = GradleDependencyVersion.Prefix(prefixVersion)
        val toCheck = GradleDependencyVersion(versionToCheck) as GradleDependencyVersion.Static
        assertEquals(expected, version.isUpdate(toCheck))
    }

    @Test
    fun testEmpty() {
        assertContains("+", "1.0", true)
        assertContains("+", "99.99", true)
        assertIsUpdate("+", "1.0", false)
        assertIsUpdate("+", "99.99", false)
    }

    @Test
    fun testClassic() {
        assertContains("1.0+", "1.0", true)
        assertContains("1.0+", "1.0.1", true)
        assertContains("1.0+", "1.1", false)
        assertContains("1.0+", "2.0", false)
        assertIsUpdate("1.0+", "1.0", false)
        assertIsUpdate("1.0+", "1.0.1", false)
        assertIsUpdate("1.0+", "1.1", true)
        assertIsUpdate("1.0+", "2.0", true)
    }

    @Test
    fun testSeparator() {
        assertContains("1.0.+", "1.0", false)
        assertContains("1.0+", "1.0.1", true)
        assertContains("1.0+", "1.1", false)
        assertContains("1.0+", "2.0", false)
        assertIsUpdate("1.0+", "1.0", false)
        assertIsUpdate("1.0+", "1.0.1", false)
        assertIsUpdate("1.0+", "1.1", true)
        assertIsUpdate("1.0+", "2.0", true)
    }
}