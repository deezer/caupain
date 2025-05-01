package com.example.plugin.policy

import com.deezer.caupain.model.Dependency
import com.deezer.caupain.model.GradleDependencyVersion
import com.deezer.caupain.model.Policy
import com.deezer.caupain.model.versionCatalog.Version

class ExamplePolicy : Policy {

    override val name: String = "my-custom-policy"

    override fun select(
        dependency: Dependency,
        currentVersion: Version.Resolved,
        updatedVersion: GradleDependencyVersion.Static
    ): Boolean {
        // This is a simple example where only the versions starting with 1.0 will be taken into account
        return updatedVersion.exactVersion.text.startsWith("1.0")
    }
}