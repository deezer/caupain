package com.deezer.dependencies.cli

import com.deezer.dependencies.DependencyUpdateChecker
import com.deezer.dependencies.NoVersionCatalogException
import com.deezer.dependencies.cli.internal.path
import com.deezer.dependencies.cli.serialization.DefaultToml
import com.deezer.dependencies.cli.serialization.decodeFromPath
import com.deezer.dependencies.formatting.Formatter
import com.deezer.dependencies.formatting.console.ConsoleFormatter
import com.deezer.dependencies.formatting.console.ConsolePrinter
import com.deezer.dependencies.formatting.html.HtmlFormatter
import com.deezer.dependencies.model.Configuration
import com.deezer.dependencies.model.Logger
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Abort
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.default
import com.github.ajalt.clikt.parameters.groups.defaultByName
import com.github.ajalt.clikt.parameters.groups.groupChoice
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.mordant.animation.coroutines.CoroutineProgressTaskAnimator
import com.github.ajalt.mordant.animation.coroutines.animateInCoroutine
import com.github.ajalt.mordant.widgets.progress.marquee
import com.github.ajalt.mordant.widgets.progress.percentage
import com.github.ajalt.mordant.widgets.progress.progressBar
import com.github.ajalt.mordant.widgets.progress.progressBarContextLayout
import com.github.ajalt.mordant.widgets.progress.timeElapsed
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM
import com.deezer.dependencies.cli.model.Configuration as ParsedConfiguration

class DependencyUpdateCheckerCli(
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val parseConfiguration: (FileSystem, Path) -> ParsedConfiguration = { fs, path ->
        DefaultToml.decodeFromPath(path, fs)
    },
    private val createUpdateChecker: (Configuration, FileSystem, Logger) -> DependencyUpdateChecker = { config, fs, logger ->
        DependencyUpdateChecker(
            configuration = config,
            fileSystem = fs,
            logger = logger
        )
    }
) : SuspendingCliktCommand(name = "dependency-update-checker") {

    private val backgroundScope = CoroutineScope(SupervisorJob() + defaultDispatcher)

    private val versionCatalogPath by
    option("-i", "--version-catalog", help = "Version catalog path")
        .path(mustExist = true, canBeFile = true, canBeDir = false, fileSystem = fileSystem)
        .default("gradle/libs.versions.toml".toPath())

    private val excluded by option("-e", "--excluded", help = "Excluded keys").multiple()

    private val configurationFile by option("-c", "--config", help = "Configuration file")
        .path(canBeFile = true, canBeDir = false, fileSystem = fileSystem)

    private val policyPluginDir by option(
        "--policy-plugin-dir",
        help = "Custom policies plugin dir"
    )
        .path(canBeFile = false, canBeDir = true, fileSystem = fileSystem)

    private val policy by option("-p", "--policy", help = "Update policy")

    private val outputType by option("-t", "--output-type", help = "Output type")
        .groupChoice(
            "console" to OutputConfig.Console(CliktConsolePrinter()),
            "html" to OutputConfig.Html(fileSystem, ioDispatcher)
        )
        .defaultByName("html")

    private val cacheDir by option(help = "Cache directory")
        .path(canBeDir = true, canBeFile = false, fileSystem = fileSystem)

    private val logLevel by mutuallyExclusiveOptions(
        option("-q", "--quiet", help = "Suppress all output")
            .flag()
            .convert { LogLevel.QUIET },
        option("-v", "--verbose", help = "Verbose output")
            .flag()
            .convert { LogLevel.VERBOSE }
    ).default(LogLevel.INFO)

    override suspend fun run() {
        var progressJob: Job? = null
        var collectProgressJob: Job? = null
        var terminalProgress: CoroutineProgressTaskAnimator<String>? = null

        val updateChecker = if (logLevel == LogLevel.INFO) {
            createUpdateChecker(createConfiguration(), fileSystem, ClicktLogger())
        } else {
            terminalProgress = progressBarContextLayout {
                marquee(DependencyUpdateChecker.MAX_TASK_NAME_LENGTH) { context }
                percentage()
                progressBar(width = 50)
                timeElapsed()
            }.animateInCoroutine(terminal, context = "Starting")

            progressJob = backgroundScope.launch {
                terminalProgress.execute()
            }

            createUpdateChecker(createConfiguration(), fileSystem, ClicktLogger()).also { checker ->
                collectProgressJob = backgroundScope.launch {
                    checker
                        .progress
                        .filterNotNull()
                        .collect { progress ->
                            terminalProgress.update {
                                context = progress.taskName
                                when (progress) {
                                    is DependencyUpdateChecker.Progress.Indeterminate -> {
                                        total = null
                                        completed = 0
                                    }

                                    is DependencyUpdateChecker.Progress.Determinate -> {
                                        total = 100
                                        completed = progress.percentage.toLong()
                                    }
                                }
                            }
                        }
                }
            }
        }

        val updates = try {
            updateChecker.checkForUpdates()
        } catch (e: NoVersionCatalogException) {
            echo(e.message, err = true)
            throw Abort()
        }
        val formatter = outputType.toFormatter()
        if (logLevel == LogLevel.VERBOSE) {
            echo("Formatting results to ${outputType.outputName}")
        }
        formatter.format(updates)
        terminalProgress?.update {
            context = "Done"
            completed = 100
        }
        collectProgressJob?.cancel()
        progressJob?.cancel()
        throw ProgramResult(0)
    }

    private suspend fun createConfiguration(): Configuration {
        val baseConfiguration = Configuration(
            versionCatalogPath = versionCatalogPath,
            excludedKeys = excluded.toSet(),
            policyPluginsDir = policyPluginDir,
            policy = policy,
            cacheDir = cacheDir
        )
        return withContext(ioDispatcher) {
            configurationFile
                ?.let { path -> parseConfiguration(fileSystem, path) }
                ?.toConfiguration(baseConfiguration)
                ?: baseConfiguration
        }
    }

    private inner class CliktConsolePrinter : ConsolePrinter {
        override fun print(message: String) {
            if (logLevel > LogLevel.QUIET) echo(message, err = false)
        }

        override fun printError(message: String) {
            if (logLevel > LogLevel.QUIET) echo(message, err = true)
        }
    }

    private inner class ClicktLogger : Logger {
        override fun debug(message: String) {
            if (logLevel >= LogLevel.VERBOSE) echo(message, err = false)
        }

        override fun info(message: String) {
            if (logLevel > LogLevel.QUIET) echo(message, err = false)
        }

        private fun echoError(message: String, throwable: Throwable?) {
            if (logLevel <= LogLevel.QUIET) return
            val errorMessage = buildString {
                append(message)
                if (throwable != null) {
                    append(": ${throwable.message}")
                }
            }
            echo(errorMessage, err = true)
        }

        override fun warn(message: String, throwable: Throwable?) {
            echoError(message, throwable)
        }

        override fun error(message: String, throwable: Throwable?) {
            echoError(message, throwable)
        }
    }

    private enum class LogLevel {
        QUIET, INFO, VERBOSE
    }
}

sealed class OutputConfig(
    help: String? = null
) : OptionGroup(null, help) {
    abstract val outputName: String

    abstract fun toFormatter(): Formatter

    class Console(private val consolePrinter: ConsolePrinter) :
        OutputConfig("Show results in console") {
        override val outputName: String = "console"

        override fun toFormatter(): Formatter = ConsoleFormatter(consolePrinter)
    }

    class Html(
        private val fileSystem: FileSystem,
        private val ioDispatcher: CoroutineDispatcher
    ) : OutputConfig("Generate HTML report") {
        private val outputPath by option("-o", "--output", help = "HTML output path")
            .path(mustExist = false, canBeFile = true, canBeDir = false, fileSystem = fileSystem)
            .default("build/reports/dependencies-update.html".toPath())

        override val outputName: String
            get() = fileSystem.canonicalize(outputPath).toString()

        override fun toFormatter(): Formatter {
            // Create the output directory if it doesn't exist
            outputPath.parent?.let { fileSystem.createDirectories(it) }
            return HtmlFormatter(
                fileSystem = fileSystem,
                path = outputPath,
                ioDispatcher = ioDispatcher
            )
        }
    }
}