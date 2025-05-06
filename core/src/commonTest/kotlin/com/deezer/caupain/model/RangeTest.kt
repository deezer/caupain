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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RangeTest {

    @Test
    fun testRangeWithNoUpperBound() {
        val range = GradleDependencyVersion("[1.0,)")
        assertIs<GradleDependencyVersion.Range>(range)
        assertFalse(GradleDependencyVersion.Exact("0.9") in range)
        assertTrue(GradleDependencyVersion.Exact("1.0") in range)
        assertTrue(GradleDependencyVersion.Exact("1.0.1") in range)
        assertTrue(GradleDependencyVersion.Exact("99.99") in range)
    }

    @Test
    fun testRangeWithNoUpperBoundAndExclusiveStart() {
        val range = GradleDependencyVersion("(1.0,)")
        assertIs<GradleDependencyVersion.Range>(range)
        assertFalse(GradleDependencyVersion.Exact("0.9") in range)
        assertFalse(GradleDependencyVersion.Exact("1.0") in range)
        assertTrue(GradleDependencyVersion.Exact("1.0.1") in range)
        assertTrue(GradleDependencyVersion.Exact("99.99") in range)
    }

    @Test
    fun testRangeWithInclusiveStartAndExclusiveEnd() {
        val range = GradleDependencyVersion("[1.1,2.0)")
        assertIs<GradleDependencyVersion.Range>(range)
        assertFalse(GradleDependencyVersion.Exact("0.9") in range)
        assertTrue(GradleDependencyVersion.Exact("1.1") in range)
        assertTrue(GradleDependencyVersion.Exact("1.1.1") in range)
        assertFalse(GradleDependencyVersion.Exact("2.0") in range)
        assertFalse(GradleDependencyVersion.Exact("2.1") in range)
    }

    @Test
    fun testRangeWithExclusiveStartAndInclusiveEnd() {
        val range = GradleDependencyVersion("(1.2, 1.5]")
        assertIs<GradleDependencyVersion.Range>(range)
        assertFalse(GradleDependencyVersion.Exact("0.9") in range)
        assertFalse(GradleDependencyVersion.Exact("1.2") in range)
        assertTrue(GradleDependencyVersion.Exact("1.2.1") in range)
        assertTrue(GradleDependencyVersion.Exact("1.5") in range)
        assertFalse(GradleDependencyVersion.Exact("2.0") in range)
    }

    @Test
    fun testRangeWithInclusiveStartAndInclusiveEnd() {
        val range = GradleDependencyVersion("[1.1,2.0]")
        assertIs<GradleDependencyVersion.Range>(range)
        assertFalse(GradleDependencyVersion.Exact("0.9") in range)
        assertTrue(GradleDependencyVersion.Exact("1.1") in range)
        assertTrue(GradleDependencyVersion.Exact("1.1.0") in range)
        assertTrue(GradleDependencyVersion.Exact("2.0") in range)
        assertFalse(GradleDependencyVersion.Exact("2.1") in range)
    }

    @Test
    fun testRangeWithExclusiveStartAndExclusiveEnd() {
        val range = GradleDependencyVersion("(1.1,2.0)")
        assertIs<GradleDependencyVersion.Range>(range)
        assertFalse(GradleDependencyVersion.Exact("0.9") in range)
        assertFalse(GradleDependencyVersion.Exact("1.1") in range)
        assertTrue(GradleDependencyVersion.Exact("1.1.0") in range)
        assertFalse(GradleDependencyVersion.Exact("2.0") in range)
        assertFalse(GradleDependencyVersion.Exact("2.1") in range)
    }
}