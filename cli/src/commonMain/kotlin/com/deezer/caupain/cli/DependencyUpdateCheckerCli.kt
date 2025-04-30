package com.deezer.caupain.cli

import ca.gosyer.appdirs.AppDirs
import com.deezer.caupain.BuildKonfig
import com.deezer.caupain.CaupainException
import com.deezer.caupain.DependencyUpdateChecker
import com.deezer.caupain.cli.internal.CAN_USE_PLUGINS
import com.deezer.caupain.cli.internal.path
import com.deezer.caupain.cli.model.GradleWrapperProperties
import com.deezer.caupain.cli.serialization.DefaultToml
import com.deezer.caupain.cli.serialization.decodeFromPath
import com.deezer.caupain.cli.serialization.decodeFromProperties
import com.deezer.caupain.formatting.FileFormatter
import com.deezer.caupain.formatting.Formatter
import com.deezer.caupain.formatting.console.ConsoleFormatter
import com.deezer.caupain.formatting.console.ConsolePrinter
import com.deezer.caupain.formatting.html.HtmlFormatter
import com.deezer.caupain.formatting.markdown.MarkdownFormatter
import com.deezer.caupain.model.Configuration
import com.deezer.caupain.model.Logger
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.completion.completionOption
import com.github.ajalt.clikt.core.Abort
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.installMordant
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.output.MordantMarkdownHelpFormatter
import com.github.ajalt.clikt.parameters.groups.default
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
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
    private val createUpdateChecker: (Configuration, String?, FileSystem, Logger) -> DependencyUpdateChecker = { config, gradleVersion, fs, logger ->
        DependencyUpdateChecker(
            configuration = config,
            currentGradleVersion = gradleVersion,
            fileSystem = fs,
            logger = logger
        )
    }
) : SuspendingCliktCommand(name = "caupain") {

    private val appDirs = AppDirs("caupain")

    private val versionCatalogPath by
    option("-i", "--version-catalog", help = "Version catalog path")
        .path(mustExist = true, canBeFile = true, canBeDir = false, fileSystem = fileSystem)
        .default("gradle/libs.versions.toml".toPath())

    private val gradleWrapperPropertiesPath by
    option("--gradle-wrapper-properties", help = "Gradle wrapper properties path")
        .path(canBeFile = true, canBeDir = false, fileSystem = fileSystem)
        .default("gradle/wrapper/gradle-wrapper.properties".toPath())

    private val excluded by option("-e", "--excluded", help = "Excluded keys").multiple()

    private val configurationFile by option("-c", "--config", help = "Configuration file")
        .path(canBeFile = true, canBeDir = false, fileSystem = fileSystem)
        .default("caupain.toml".toPath())

    private val policyPluginDir by option(
        "--policy-plugin-dir",
        help = "Custom policies plugin dir",
        hidden = !CAN_USE_PLUGINS
    ).path(canBeFile = false, canBeDir = true, fileSystem = fileSystem)

    private val policy by option("-p", "--policy", help = "Update policy")

    private val outputType by option("-t", "--output-type", help = "Output type")
        .choice(
            CONSOLE_TYPE to ParsedConfiguration.OutputType.CONSOLE,
            HTML_TYPE to ParsedConfiguration.OutputType.HTML,
            MARKDOWN_TYPE to ParsedConfiguration.OutputType.MARKDOWN
        )
        .default(
            value = ParsedConfiguration.OutputType.CONSOLE,
            defaultForHelp = CONSOLE_TYPE
        )

    private val outputPath by option("-o", "--output", help = "Report output path")
        .path(canBeFile = true, canBeDir = false, fileSystem = fileSystem)

    private val cacheDir by option(help = "Cache directory. This is not used if --no-cache is set")
        .path(canBeDir = true, canBeFile = false, fileSystem = fileSystem)
        .default(
            value = appDirs.getUserCacheDir().toPath(),
            defaultForHelp = "user cache dir"
        )

    private val doNotCache by option("--no--cache", help = "Disable HTTP cache").flag()

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
    ).flag()

    private val version by option(help = "Print version and exit").flag()

    init {
        installMordant()
        context {
            helpFormatter = { ctx ->
                MordantMarkdownHelpFormatter(
                    context = ctx,
                    showDefaultValues = true,
                    showRequiredTag = true
                )
            }
        }
        completionOption(help = "Generate completion script", hidden = true)
    }

    override suspend fun run() {
        if (version) throw PrintMessage("Caupain v${BuildKonfig.VERSION}")

        val backgroundScope = CoroutineScope(SupervisorJob() + defaultDispatcher)

        val configuration = loadConfiguration()
        val finalConfiguration = createConfiguration(configuration)
        if (finalConfiguration.policyPluginsDir != null && !CAN_USE_PLUGINS) {
            echo("Policy plugins are not supported on this platform", err = true)
        }
        val updateChecker =
            createUpdateChecker(
                finalConfiguration,
                loadGradleVersion(configuration),
                fileSystem,
                ClicktLogger()
            )
        val progress = backgroundScope.createProgress(updateChecker.progress)

        val updates = try {
            updateChecker.checkForUpdates()
        } catch (e: CaupainException) {
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

    private fun CoroutineScope.createProgress(progressFlow: Flow<DependencyUpdateChecker.Progress?>): CoroutineProgressTaskAnimator<String>? {
        return if (logLevel == LogLevel.DEFAULT) {
            val terminalProgress = progressBarContextLayout {
                marquee(DependencyUpdateChecker.MAX_TASK_NAME_LENGTH) { context }
                percentage()
                progressBar(width = 50)
                timeElapsed()
            }.animateInCoroutine(terminal, context = "Starting")

            launch {
                terminalProgress.execute()
            }

            launch {
                progressFlow
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
        } else {
            null
        }
    }

    private fun createConfiguration(parsedConfiguration: ParsedConfiguration?): Configuration {
        val baseConfiguration = Configuration(
            versionCatalogPath = versionCatalogPath,
            excludedKeys = excluded.toSet(),
            policyPluginsDir = policyPluginDir,
            policy = policy,
            cacheDir = if (doNotCache) null else cacheDir,
            debugHttpCalls = debugHttpCalls,
        )
        return parsedConfiguration?.toConfiguration(baseConfiguration) ?: baseConfiguration
    }

    private suspend fun loadGradleVersion(parsedConfiguration: ParsedConfiguration?): String? {
        return withContext(ioDispatcher) {
            val gradleWrapperPropertiesPath = (parsedConfiguration?.gradleWrapperPropertiesPath
                ?: gradleWrapperPropertiesPath)
                .takeIf { fileSystem.exists(it) }
                ?: return@withContext null
            val distributionUrl = decodeFromProperties<GradleWrapperProperties>(
                fileSystem = fileSystem,
                path = gradleWrapperPropertiesPath
            ).distributionUrl ?: return@withContext null
            GRADLE_URL_REGEX.find(distributionUrl)?.groupValues?.getOrNull(1)
        }
    }

    private suspend fun loadConfiguration(): ParsedConfiguration? {
        return withContext(ioDispatcher) {
            configurationFile
                .takeIf { fileSystem.exists(it) }
                ?.let { parseConfiguration(fileSystem, it) }
        }
    }

    private fun outputFormatter(configuration: ParsedConfiguration?): Formatter {
        return when (configuration?.outputType ?: outputType) {
            ParsedConfiguration.OutputType.CONSOLE -> ConsoleFormatter(CliktConsolePrinter())

            ParsedConfiguration.OutputType.HTML -> HtmlFormatter(
                fileSystem = fileSystem,
                ioDispatcher = ioDispatcher,
                path = configuration?.outputPath
                    ?: outputPath
                    ?: "build/reports/dependencies-update.html".toPath()
            )

            ParsedConfiguration.OutputType.MARKDOWN -> MarkdownFormatter(
                fileSystem = fileSystem,
                ioDispatcher = ioDispatcher,
                path = configuration?.outputPath
                    ?: outputPath
                    ?: "build/reports/dependencies-update.md".toPath()
            )
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
        private const val MARKDOWN_TYPE = "markdown"
        private val GRADLE_URL_REGEX =
            Regex("https://services.gradle.org/distributions/gradle-(.*)-.*.zip")
    }
}