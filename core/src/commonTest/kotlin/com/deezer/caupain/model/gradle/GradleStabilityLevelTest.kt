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

package com.deezer.caupain.model.gradle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GradleStabilityLevelTest {

    @Test
    fun testKeys() {
        assertEquals(
            listOf("stable", "rc", "milestone", "release-nightly", "nightly"),
            GradleStabilityLevel.entries.map { it.key }
        )
    }

    @Test
    fun testFromKey() {
        for (level in GradleStabilityLevel.entries) {
            assertEquals(level, GradleStabilityLevel.fromKey(level.key))
        }
    }

    @Test
    fun testFromKeyIgnoresCase() {
        assertEquals(GradleStabilityLevel.RELEASE_NIGHTLY, GradleStabilityLevel.fromKey("Release-Nightly"))
        assertEquals(GradleStabilityLevel.RC, GradleStabilityLevel.fromKey("RC"))
    }

    @Test
    fun testFromUnknownKey() {
        assertNull(GradleStabilityLevel.fromKey("unknown"))
        // Serial names and enum names are not keys
        assertNull(GradleStabilityLevel.fromKey("releaseNightly"))
        assertNull(GradleStabilityLevel.fromKey("RELEASE_NIGHTLY"))
    }
}
