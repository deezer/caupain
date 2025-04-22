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
                currentVersion = Version.Simple(GradleDependencyVersion(currentVersion)),
                updatedVersion = GradleDependencyVersion(updatedVersion) as GradleDependencyVersion.Single
            )
        )
    }

    @Test
    fun testVersionLevelPolicy() {
        AndroidXVersionLevelPolicy.testSelect(
            currentVersion = "1.0.0",
            updatedVersion = "1.0.1",
            expected = true
        )
        AndroidXVersionLevelPolicy.testSelect(
            currentVersion = "1.0.0-beta01",
            updatedVersion = "1.0.0",
            expected = true
        )
        AndroidXVersionLevelPolicy.testSelect(
            currentVersion = "1.0.0-beta01",
            updatedVersion = "1.0.1-alpha01",
            expected = false
        )
        AndroidXVersionLevelPolicy.testSelect(
            currentVersion = "1.0.0",
            updatedVersion = "1.0.1-SNAPSHOT",
            expected = false
        )
        AndroidXVersionLevelPolicy.testSelect(
            currentVersion = "1.0.0-SNAPSHOT",
            updatedVersion = "1.0.1-SNAPSHOT",
            expected = true
        )
    }
}