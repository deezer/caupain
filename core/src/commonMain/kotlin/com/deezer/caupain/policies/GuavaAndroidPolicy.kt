package com.deezer.caupain.policies

import com.deezer.caupain.model.Dependency
import com.deezer.caupain.model.GradleDependencyVersion
import com.deezer.caupain.model.Policy
import com.deezer.caupain.model.group
import com.deezer.caupain.model.name
import com.deezer.caupain.model.versionCatalog.Version

/**
 * This is a policy implementation that rejects Guava updates with the prefix "-jre" when the current
 * version uses "-android" prefix. This is needed because alphabetically, "-jre" is greater than "-android",
 * but in practice, the "-jre" versions are not compatible with Android and should not be selected
 * as updates for dependencies using the "-android" prefix.
 */
public object GuavaAndroidPolicy : Policy {

    override val name: String
        get() = "guava-android"

    override val description: String
        get() = "Policy that rejects Guava updates with the prefix \"-jre\" when the current " +
                "version uses \"-android\" prefix. This is needed because alphabetically, \"-jre\" is greater than \"-android\", " +
                "but in practice, the \"-jre\" versions are not compatible with Android and should not be selected " +
                "as updates for dependencies using the \"-android\" prefix"

    override fun select(
        dependency: Dependency,
        currentVersion: Version.Resolved,
        updatedVersion: GradleDependencyVersion.Static
    ): Boolean {
        // We only want to apply this policy for Guava library
        if (dependency.group != GUAVA_GROUP || dependency.name != GUAVA_ARTIFACT) return true

        val resolvedCurrentVersionString = when (currentVersion) {
            is Version.Simple -> currentVersion.value as? GradleDependencyVersion.Static
            is Version.Rich -> currentVersion.probableSelectedVersion
        }?.exactVersion?.toString() ?: return true
        val updatedVersionString = updatedVersion.exactVersion.toString()

        return !resolvedCurrentVersionString.endsWith("-android")
                || !updatedVersionString.endsWith("-jre")
    }

    private const val GUAVA_GROUP = "com.google.guava"
    private const val GUAVA_ARTIFACT = "guava"
}
