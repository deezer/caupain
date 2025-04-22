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
        val toCheck = GradleDependencyVersion(versionToCheck) as GradleDependencyVersion.Single
        assertEquals(expected, version.contains(toCheck))
    }

    private fun assertIsUpdate(
        prefixVersion: String,
        versionToCheck: String,
        expected: Boolean
    ) {
        val version = GradleDependencyVersion.Prefix(prefixVersion)
        val toCheck = GradleDependencyVersion(versionToCheck) as GradleDependencyVersion.Single
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