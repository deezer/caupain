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