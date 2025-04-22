package com.deezer.dependencies.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GradleDependencyVersionTest {

    private fun checkParsing(
        text: String,
        expectedVersion: GradleDependencyVersion
    ) {
        assertEquals(
            expected = expectedVersion,
            actual = GradleDependencyVersion(text)
        )
    }

    @Test
    fun testExact() {
        checkParsing("1.3", GradleDependencyVersion.Exact("1.3"))
        checkParsing("1.3.0-beta3", GradleDependencyVersion.Exact("1.3.0-beta3"))
        checkParsing(
            "1.0-20150201.131010-1",
            GradleDependencyVersion.Exact("1.0-20150201.131010-1")
        )
    }

    @Test
    fun testExactSorting() {
        val versions = listOf(
            "1.1",
            "1.1.2",
            "1.1.2-rc",
            "1.1.2.1",
            "1.1.2-dev",
        )
        val parsedVersions = versions.map { versionText ->
            GradleDependencyVersion(versionText) as GradleDependencyVersion.Exact
        }
        assertEquals(
            expected = listOf(
                parsedVersions[0],
                parsedVersions[4],
                parsedVersions[2],
                parsedVersions[1],
                parsedVersions[3],
            ),
            actual = parsedVersions.sorted()
        )
    }

    @Test
    fun testRangeParsing() {
        fun String?.toBound(isExclusive: Boolean): GradleDependencyVersion.Range.Bound? {
            return this?.let { value ->
                GradleDependencyVersion.Range.Bound(
                    value = GradleDependencyVersion(value) as GradleDependencyVersion.Single,
                    isExclusive = isExclusive
                )
            }
        }

        fun checkBounds(
            text: String,
            lowerBound: String?,
            upperBound: String?,
            isLowerBoundExclusive: Boolean = false,
            isUpperBoundExclusive: Boolean = false,
        ) {
            val range = GradleDependencyVersion(text) as GradleDependencyVersion.Range
            assertEquals(lowerBound.toBound(isLowerBoundExclusive), range.lowerBound)
            assertEquals(upperBound.toBound(isUpperBoundExclusive), range.upperBound)
        }
        checkBounds(
            text = "[1.1, 2.0]",
            lowerBound = "1.1",
            upperBound = "2.0",
            isLowerBoundExclusive = false,
            isUpperBoundExclusive = false
        )
        checkBounds(
            text = "(1.1, 2.0)",
            lowerBound = "1.1",
            upperBound = "2.0",
            isLowerBoundExclusive = true,
            isUpperBoundExclusive = true
        )
        checkBounds(
            text = "(1.2, 1.5]",
            lowerBound = "1.2",
            upperBound = "1.5",
            isLowerBoundExclusive = true,
            isUpperBoundExclusive = false
        )
        checkBounds(
            text = "[1.1, 2.0)",
            lowerBound = "1.1",
            upperBound = "2.0",
            isLowerBoundExclusive = false,
            isUpperBoundExclusive = true
        )
        checkBounds(
            text = "]1.2, 1.5]",
            lowerBound = "1.2",
            upperBound = "1.5",
            isLowerBoundExclusive = true,
            isUpperBoundExclusive = false
        )
        checkBounds(
            text = "[1.1, 2.0[",
            lowerBound = "1.1",
            upperBound = "2.0",
            isLowerBoundExclusive = false,
            isUpperBoundExclusive = true
        )
    }

    @Test
    fun testPrefixParsing() {
        val prefix = GradleDependencyVersion("1.3+")
        assertIs<GradleDependencyVersion.Prefix>(prefix)
        assertEquals(GradleDependencyVersion.Exact("1.3"), prefix.baseVersion)
        assertTrue(GradleDependencyVersion.Exact("1.3") in prefix)
        assertTrue(GradleDependencyVersion.Exact("1.3.5") in prefix)
        assertFalse(GradleDependencyVersion.Exact("1.4.1") in prefix)
        assertEquals(
            expected = GradleDependencyVersion.Exact("1.3"),
            actual = (GradleDependencyVersion("1.3.+") as GradleDependencyVersion.Prefix).baseVersion
        )
        assertNull((GradleDependencyVersion("+") as GradleDependencyVersion.Prefix).baseVersion)
    }

    @Test
    fun testLatestParsing() {
        checkParsing("latest.release", GradleDependencyVersion.Latest("latest.release"))
        checkParsing("latest.integration", GradleDependencyVersion.Latest("latest.integration"))
    }

    @Test
    fun testSnapshotParsing() {
        val snapshot = GradleDependencyVersion("1.3-SNAPSHOT")
        assertIs<GradleDependencyVersion.Snapshot>(snapshot)
        assertEquals(snapshot.exactVersion, GradleDependencyVersion.Exact("1.3"))
    }
}