package com.deezer.caupain.cli

import com.deezer.caupain.DependencyUpdateChecker
import com.deezer.caupain.NoVersionCatalogException
import com.deezer.caupain.cli.internal.path
import com.deezer.caupain.cli.serialization.DefaultToml
import com.deezer.caupain.cli.serialization.decodeFromPath
import com.deezer.caupain.formatting.FileFormatter
import com.deezer.caupain.formatting.Formatter
import com.deezer.caupain.formatting.console.ConsoleFormatter
import com.deezer.caupain.formatting.console.ConsolePrinter
import com.deezer.caupain.formatting.html.HtmlFormatter
import com.deezer.caupain.model.Configuration
import com.deezer.caupain.model.Logger
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM
import com.deezer.caupain.cli.model.Configuration as ParsedConfiguration

class DependencyUpdateCheckerCli(
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
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
) : SuspendingCliktCommand(name = "caupain") {

    private val versionCatalogPath by
    option("-i", "--version-catalog", help = "Version catalog path")
        .path(mustExist = true, canBeFile = true, canBeDir = false, fileSystem = fileSystem)
        .default("gradle/libs.versions.toml".toPath())

    private val excluded by option("-e", "--excluded", help = "Excluded keys").multiple()

    private val configurationFile by option("-c", "--config", help = "Configuration file")
        .path(canBeFile = true, canBeDir = false, fileSystem = fileSystem)
        .default("caupain.toml".toPath())

    private val policyPluginDir by option(
        "--policy-plugin-dir",
        help = "Custom policies plugin dir"
    )
        .path(canBeFile = false, canBeDir = true, fileSystem = fileSystem)

    private val policy by option("-p", "--policy", help = "Update policy")

    private val outputType by option("-t", "--output-type", help = "Output type")
        .groupChoice(
            CONSOLE_TYPE to OutputConfig.Console(CliktConsolePrinter()),
            HTML_TYPE to OutputConfig.Html(fileSystem, ioDispatcher)
        )
        .defaultByName(CONSOLE_TYPE)

    private val cacheDir by option(help = "Cache directory")
        .path(canBeDir = true, canBeFile = false, fileSystem = fileSystem)

    private val logLevel by mutuallyExclusiveOptions(
        option("-q", "--quiet", help = "Suppress all output")
            .flag()
            .convert { LogLevel.QUIET },
        option("-v", "--verbose", help = "Verbose output")
            .flag()
            .convert { LogLevel.VERBOSE },
        option("-d", "--debug", help = "Debug output")
            .flag()
            .convert { LogLevel.DEBUG }
    ).default(LogLevel.DEFAULT)

    private val debugHttpCalls by option(
        "--debug-http-calls",
        help = "Enable debugging for HTTP calls"
    ).flag(default = false)

    override suspend fun run() {
        val backgroundScope = CoroutineScope(SupervisorJob() + defaultDispatcher)

        val configuration = loadConfiguration()
        val updateChecker =
            createUpdateChecker(createConfiguration(configuration), fileSystem, ClicktLogger())
        val progress = if (logLevel > LogLevel.DEFAULT) {
            null
        } else {
            val terminalProgress = progressBarContextLayout {
                marquee(DependencyUpdateChecker.MAX_TASK_NAME_LENGTH) { context }
                percentage()
                progressBar(width = 50)
                timeElapsed()
            }.animateInCoroutine(terminal, context = "Starting")

            backgroundScope.launch {
                terminalProgress.execute()
            }

            backgroundScope.launch {
                updateChecker
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

            terminalProgress
        }

        val updates = try {
            updateChecker.checkForUpdates()
        } catch (e: NoVersionCatalogException) {
            echo(e.message, err = true)
            throw Abort()
        }
        val formatter = outputFormatter(configuration)
        formatter.format(updates)
        progress?.clear()
        backgroundScope.cancel()
        if (logLevel >= LogLevel.DEFAULT && formatter is FileFormatter) {
            echo("Report generated at ${formatter.outputPath}")
        }
        throw ProgramResult(0)
    }

    private fun createConfiguration(parsedConfiguration: ParsedConfiguration?): Configuration {
        val baseConfiguration = Configuration(
            versionCatalogPath = versionCatalogPath,
            excludedKeys = excluded.toSet(),
            policyPluginsDir = policyPluginDir,
            policy = policy,
            cacheDir = cacheDir,
            debugHttpCalls = debugHttpCalls,
        )
        return parsedConfiguration?.toConfiguration(baseConfiguration) ?: baseConfiguration
    }

    private suspend fun loadConfiguration(): ParsedConfiguration? {
        return withContext(ioDispatcher) {
            configurationFile
                .takeIf { fileSystem.exists(it) }
                ?.let { parseConfiguration(fileSystem, it) }
        }
    }

    private fun outputFormatter(configuration: ParsedConfiguration?): Formatter {
        return when (configuration?.outputType) {
            ParsedConfiguration.OutputType.CONSOLE -> ConsoleFormatter(CliktConsolePrinter())

            ParsedConfiguration.OutputType.HTML -> HtmlFormatter(
                fileSystem = fileSystem,
                ioDispatcher = ioDispatcher,
                path = configuration.outputPath
                    ?: (outputType as? OutputConfig.Html)?.outputPath
                    ?: OutputConfig.Html.DEFAULT_PATH
            )

            else -> outputType.toFormatter()
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
            if (logLevel >= LogLevel.DEBUG) echo(message, err = false)
        }

        override fun info(message: String) {
            if (logLevel >= LogLevel.VERBOSE) echo(message, err = false)
        }

        override fun lifecycle(message: String) {
            if (logLevel >= LogLevel.DEFAULT) echo(message, err = false)
        }

        private fun echoError(message: String, throwable: Throwable?) {
            if (logLevel <= LogLevel.QUIET) return
            val errorMessage = buildString {
                append(message)
                when {
                    throwable == null -> Unit

                    logLevel >= LogLevel.DEBUG -> {
                        appendLine()
                        append(throwable.stackTraceToString())
                    }

                    else -> append(": ${throwable.message}")
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
        QUIET, DEFAULT, VERBOSE, DEBUG
    }

    companion object {
        private const val CONSOLE_TYPE = "console"
        private const val HTML_TYPE = "html"
    }
}

sealed class OutputConfig(
    help: String? = null
) : OptionGroup(null, help) {
    abstract fun toFormatter(): Formatter

    class Console(private val consolePrinter: ConsolePrinter) : OutputConfig("Show results in console") {

        override fun toFormatter(): Formatter = ConsoleFormatter(consolePrinter)
    }

    class Html(
        private val fileSystem: FileSystem,
        private val ioDispatcher: CoroutineDispatcher
    ) : OutputConfig("Generate HTML report") {
        val outputPath by option("-o", "--output", help = "HTML output path")
            .path(mustExist = false, canBeFile = true, canBeDir = false, fileSystem = fileSystem)
            .default(DEFAULT_PATH)

        override fun toFormatter(): Formatter {
            // Create the output directory if it doesn't exist
            outputPath.parent?.let { fileSystem.createDirectories(it) }
            return HtmlFormatter(
                fileSystem = fileSystem,
                path = outputPath,
                ioDispatcher = ioDispatcher
            )
        }

        companion object {
            val DEFAULT_PATH = "build/reports/dependencies-update.html".toPath()
        }
    }
}