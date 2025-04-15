package com.deezer.dependencies.cli

import com.deezer.dependencies.DefaultDependencyUpdateChecker
import com.deezer.dependencies.DependencyUpdateChecker
import com.deezer.dependencies.cli.internal.path
import com.deezer.dependencies.model.Configuration
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM

class DependencyUpdateCheckerCli(
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val createUpdateChecker: (Configuration, FileSystem) -> DependencyUpdateChecker = { config, fs ->
        DefaultDependencyUpdateChecker(
            configuration = config,
            fileSystem = fs
        )
    }
) : SuspendingCliktCommand() {

    private val versionCatalogPath by
    option("-i", "--version-catalog", help = "Version catalog path")
        .path(mustExist = true, canBeFile = true, canBeDir = false, fileSystem = fileSystem)
        .default("gradle/libs.versions.toml".toPath())

    private val excluded by option(help = "Excluded keys").multiple()

    private val configurationFile by option("-c", "--config", help = "Configuration file")
        .path(canBeFile = true, canBeDir = false, fileSystem = fileSystem)

    private val policyPluginDir by option("--policy-plugin-dir", help = "Custom policies plugin dir")
        .path(canBeFile = false, canBeDir = true, fileSystem = fileSystem)

    private val policy by option("-p", "--policy", help = "Update policy")

    private val updateChecker by lazy {
        createUpdateChecker(
            Configuration(
                versionCatalogPath = versionCatalogPath,
                excludedKeys = excluded.toSet(),
                policy = policy,
                policyPluginDir = policyPluginDir
            ),
            fileSystem
        )
    }

    // TODO : parse config file
    // TODO : use progress and listener
    // TODO : output
    override suspend fun run() {
        updateChecker.checkForUpdates()
    }
}