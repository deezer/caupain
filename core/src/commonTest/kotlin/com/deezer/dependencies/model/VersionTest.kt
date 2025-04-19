package com.deezer.dependencies.model

import com.deezer.dependencies.model.versionCatalog.Version
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