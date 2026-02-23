/*
 * MIT License
 *
 * Copyright (c) 2025 Deezer
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
@file:Suppress("UnusedImport")

package com.deezer.caupain.cli

import ca.gosyer.appdirs.AppDirs
import com.deezer.caupain.CaupainException
import com.deezer.caupain.CorruptedCacheException
import com.deezer.caupain.DependencyUpdateChecker
import com.deezer.caupain.DependencyVersionsReplacer
import com.deezer.caupain.cli.internal.OutputSink
import com.deezer.caupain.cli.internal.path
import com.deezer.caupain.cli.internal.sink
import com.deezer.caupain.cli.model.Formatter
import com.deezer.caupain.cli.model.GradleWrapperProperties
import com.deezer.caupain.cli.resolver.CLISelfUpdateResolver
import com.deezer.caupain.cli.serialization.DefaultToml
import com.deezer.caupain.cli.serialization.decodeFromPath
import com.deezer.caupain.cli.serialization.decodeFromProperties
import com.deezer.caupain.formatting.console.ConsoleFormatter
import com.deezer.caupain.formatting.console.ConsolePrinter
import com.deezer.caupain.formatting.html.HtmlFormatter
import com.deezer.caupain.formatting.json.JsonFormatter
import com.deezer.caupain.formatting.markdown.MarkdownFormatter
import com.deezer.caupain.formatting.model.Input
import com.deezer.caupain.model.Configuration
import com.deezer.caupain.model.Logger
import com.deezer.caupain.model.gradle.GradleStabilityLevel
import com.deezer.caupain.policies.StabilityLevelPolicy
import com.deezer.caupain.resolver.SelfUpdateResolver
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.completion.completionOption
import com.github.ajalt.clikt.core.Abort
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.installMordant
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.output.HelpFormatter
import com.github.ajalt.clikt.output.MordantMarkdownHelpFormatter
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.cooccurring
import com.github.ajalt.clikt.parameters.groups.default
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.nullableFlag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.unique
import com.github.ajalt.clikt.parameters.options.versionOption
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
import kotlin.time.TimeSource
import com.deezer.caupain.cli.model.Configuration as ParsedConfiguration

@Suppress("LongParameterList") // Needed for dependency injection
class CaupainCLI(
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val parseConfiguration: (FileSystem, Path) -> ParsedConfiguration = { fs, path ->
        DefaultToml.decodeFromPath(path, fs)
    },
    @Suppress("NoNameShadowing") // Ok to repeat here for clarity
    private val createUpdateChecker: (Configuration, String?, FileSystem, CoroutineDispatcher, Logger, SelfUpdateResolver) -> DependencyUpdateChecker = { config, gradleVersion, fs, ioDispatcher, logger, selfUpdateResolver ->
        DependencyUpdateChecker(
            configuration = config,
            currentGradleVersion = gradleVersion,
            fileSystem = fs,
            ioDispatcher = ioDispatcher,
            logger = logger,
            selfUpdateResolver = selfUpdateResolver
        )
    },
    @Suppress("NoNameShadowing") // Ok to repeat here for clarity
    private val createVersionReplacer: (FileSystem, CoroutineDispatcher, CoroutineDispatcher) -> DependencyVersionsReplacer = { filesystem, ioDispatcher, defaultDispatcher ->
        DependencyVersionsReplacer(
            fileSystem = filesystem,
            ioDispatcher = ioDispatcher,
            defaultDispatcher = defaultDispatcher
        )
    },
    private val appDirs: AppDirs = AppDirs { appName = "caupain" },
) : SuspendingCliktCommand(name = "caupain") {

    private val versionCatalogPaths by
    option(
        "-i",
        "--version-catalog",
        help = "Version catalog path. Use multiple times to use multiple version catalogs",
        helpTags = mapOf(HelpFormatter.Tags.DEFAULT to Configuration.DEFAULT_CATALOG_PATH.toString()),
    )
        .path(mustExist = true, canBeFile = true, canBeDir = false, fileSystem = fileSystem)
        .multiple()

    private val gradleWrapperPropertiesPath by
    option(
        "--gradle-wrapper-properties",
        help = "Gradle wrapper properties path",
        helpTags = mapOf(HelpFormatter.Tags.DEFAULT to DEFAULT_GRADLE_PROPERTIES_PATH.toString())
    )
        .path(canBeFile = true, canBeDir = false, fileSystem = fileSystem)

    private val excluded by option("-e", "--excluded", help = "Excluded keys")
        .multiple()
        .unique()

    private val configurationFile by option("-c", "--config", help = "Configuration file")
        .path(canBeFile = true, canBeDir = false, fileSystem = fileSystem)
        .default("caupain.toml".toPath())

    private val policyPluginDir by option(
        "--policy-plugin-dir",
        help = "Custom policies plugin dir",
        hidden = !BuildConfig.CAN_USE_PLUGINS
    ).path(canBeFile = false, canBeDir = true, fileSystem = fileSystem)

    private val policies by option(
        "-p",
        "--policy",
        help = "Update policy (default: `stability-level`). Multiple policies can be specified by using this option multiple times, and will be combined (a version must satisfy all policies to be accepted)."
    ).multiple()

    private val listPolicies by option("--list-policies", help = "List available policies")
        .flag()

    private val gradleStabilityLevel by option(
        help = "Gradle stability level",
        helpTags = mapOf(HelpFormatter.Tags.DEFAULT to "stable")
    )
        .choice(
            "stable" to GradleStabilityLevel.STABLE,
            "rc" to GradleStabilityLevel.RC,
            "milestone" to GradleStabilityLevel.MILESTONE,
            "release-nightly" to GradleStabilityLevel.RELEASE_NIGHTLY,
            "nightly" to GradleStabilityLevel.NIGHTLY
        )

    private val outputTypes by option(
        "-t", "--output-type",
        help = "Output type",
        helpTags = mapOf(HelpFormatter.Tags.DEFAULT to CONSOLE_TYPE)
    )
        .choice(
            CONSOLE_TYPE to ParsedConfiguration.OutputType.CONSOLE,
            HTML_TYPE to ParsedConfiguration.OutputType.HTML,
            MARKDOWN_TYPE to ParsedConfiguration.OutputType.MARKDOWN,
            JSON_TYPE to ParsedConfiguration.OutputType.JSON
        )
        .multiple()

    private val outputSink by option(
        "-o", "--output",
        help = "Report output path (or - if you want to ouput to standard output). If a file is specified, only used if a single output type is specified",
        helpTags = mapOf(HelpFormatter.Tags.DEFAULT to "$DEFAULT_OUTPUT_DIR/$DEFAULT_OUTPUT_BASE_NAME.(html|md|json)")
    ).sink(truncateExisting = true, fileSystem = fileSystem)

    private val multiplePathOptions by MultiplePathOptions(fileSystem).cooccurring()

    private val showVersionReferences by option(help = "Show versions references update summary in the report")
        .flag()

    private val replace by option(
        "--in-place",
        help = "Replace versions in version catalog in place"
    ).flag()

    private val releaseNoteOptions by ReleaseNoteOptions().cooccurring()

    private val cacheDir by option(
        help = "Cache directory. This is not used if --no-cache is set",
        helpTags = mapOf(HelpFormatter.Tags.DEFAULT to "user cache dir")
    ).path(canBeDir = true, canBeFile = false, fileSystem = fileSystem)

    private val doNotCache by option("--no-cache", help = "Disable HTTP cache")
        .flag()

    private val cleanCache by option("--clean-cache", help = "Clean the cache before running")
        .flag()

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

    private val verifyExistence by option(
        "--verify-existence",
        help = "Verify that .pom file exists before accepting version updates (warning: may slow down checks)"
    ).flag()

    private val timesource = TimeSource.Monotonic

    init {
        installMordant()
        context {
            helpFormatter = { ctx ->
                MordantMarkdownHelpFormatter(
                    context = ctx,
                    showDefaultValues = true,
                )
            }
        }
        completionOption(help = "Generate completion script", hidden = true)
        versionOption(BuildConfig.VERSION)
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    override suspend fun run() {
        val start = timesource.markNow()

        val backgroundScope = CoroutineScope(SupervisorJob() + defaultDispatcher)
        val logger = ClicktLogger()

        val configuration = loadConfiguration()
        validateConfiguration(parsedConfiguration = configuration, logger = logger)
        val finalConfiguration = createConfiguration(configuration)
        if (finalConfiguration.policyPluginsDir != null && !BuildConfig.CAN_USE_PLUGINS) {
            echo("Policy plugins are not supported on this platform", err = true)
        } else if (
            replace
            && (finalConfiguration.versionCatalogPaths.count() > 1 || !finalConfiguration.onlyCheckStaticVersions)
        ) {
            throw ReplaceNotAvailableException()
        }

        val updateChecker =
            createUpdateChecker(
                finalConfiguration,
                loadGradleVersion(configuration),
                fileSystem,
                ioDispatcher,
                logger,
                CLISelfUpdateResolver(
                    logger = logger,
                    ioDispatcher = ioDispatcher,
                    fileSystem = fileSystem,
                    githubToken = finalConfiguration.githubToken
                )
            )

        if (listPolicies) {
            throw PrintMessage(
                statusCode = 0,
                message = buildString {
                    appendLine("Available policies:")
                    for (policy in updateChecker.policies) {
                        append("- ")
                        append(policy.name)
                        if (policy.description.isNullOrEmpty()) {
                            appendLine()
                        } else {
                            append(": ")
                            appendLine(policy.description)
                        }
                    }
                }
            )
        }

        val progress = backgroundScope.createProgress(updateChecker.progress)

        val updates = try {
            updateChecker.checkForUpdates()
        } catch (_: CorruptedCacheException) {
            echo(
                "The cache is corrupted. Try to run again with --clean-cache to refresh it",
                err = true
            )
            throw Abort()
        } catch (e: CaupainException) {
            echo(e.message, err = true)
            throw Abort()
        }

        val formatters = outputFormatters(configuration)
        val formatToConsole = formatters.any { it is Formatter.Console }
        if (formatToConsole) {
            // Clear progress early to avoid sending progress to console while formatting
            progress?.clear()
            backgroundScope.cancel()
        }

        format(
            input = Input(
                updateResult = updates,
                showVersionReferences = showVersionReferences
                        || configuration?.showVersionReferences == true
            ),
            formatters = formatters,
            configuration = configuration,
        )

        if (formatToConsole) {
            progress?.clear()
            backgroundScope.cancel()
        }

        if (replace) {
            createVersionReplacer(fileSystem, ioDispatcher, defaultDispatcher)
                .replaceVersions(
                    versionCatalogPath = finalConfiguration.versionCatalogPaths.single(),
                    updateResult = updates
                )
        }

        if (logLevel > LogLevel.DEFAULT) {
            start.elapsedNow().toComponents { minutes, seconds, nanoseconds ->
                val milliseconds = nanoseconds / 1_000_000
                echo(
                    buildString {
                        append("Execution time: ")
                        if (minutes > 0) append("$minutes min ")
                        append("$seconds.$milliseconds sec")
                    }
                )
            }
        }

        backgroundScope.cancel()

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

    @Suppress(
        "CyclomaticComplexMethod", // Just a lot of parameters and elvis operators
        "NullableBooleanCheck", // Used for readibility
        "UseOrEmpty", // Used for readibility
    )
    private fun createConfiguration(parsedConfiguration: ParsedConfiguration?): Configuration {
        return Configuration(
            repositories = parsedConfiguration?.repositories?.map { it.toModel() }
                ?: Configuration.DEFAULT_REPOSITORIES,
            pluginRepositories = parsedConfiguration?.pluginRepositories?.map { it.toModel() }
                ?: Configuration.DEFAULT_PLUGIN_REPOSITORIES,
            versionCatalogPaths = versionCatalogPaths.takeUnless { it.isEmpty() }
                ?: parsedConfiguration?.versionCatalogPaths
                ?: parsedConfiguration?.versionCatalogPath?.let(::listOf)
                ?: listOf(Configuration.DEFAULT_CATALOG_PATH),
            excludedKeys = excluded.takeUnless { it.isEmpty() }
                ?: parsedConfiguration?.excludedKeys
                ?: emptySet(),
            excludedLibraries = parsedConfiguration?.excludedLibraries.orEmpty(),
            excludedPlugins = parsedConfiguration?.excludedPlugins.orEmpty(),
            policies = policies
                .takeUnless { it.isEmpty() }
                ?: parsedConfiguration?.policies
                ?: parsedConfiguration?.policy?.let(::listOf)
                ?: listOf(StabilityLevelPolicy.name),
            policyPluginsDir = policyPluginDir
                ?: parsedConfiguration?.policyPluginDir,
            cacheDir = if (doNotCache) {
                null
            } else {
                cacheDir
                    ?: parsedConfiguration?.cacheDir
                    ?: appDirs.getUserCacheDir().toPath()
            },
            cleanCache = cleanCache,
            debugHttpCalls = debugHttpCalls,
            onlyCheckStaticVersions = parsedConfiguration?.onlyCheckStaticVersions != false,
            gradleStabilityLevel = gradleStabilityLevel
                ?: parsedConfiguration?.gradleStabilityLevel
                ?: GradleStabilityLevel.STABLE,
            checkIgnored = parsedConfiguration?.checkIgnored == true,
            searchReleaseNote = releaseNoteOptions?.searchReleaseNote
                ?: parsedConfiguration?.searchReleaseNote
                ?: false,
            githubToken = releaseNoteOptions?.githubToken
                ?: parsedConfiguration?.githubToken
                ?: currentContext.readEnvvar("CAUPAIN_GITHUB_TOKEN"),
            verifyExistence = verifyExistence || parsedConfiguration?.verifyExistence == true
        )
    }

    private fun validateConfiguration(
        parsedConfiguration: ParsedConfiguration?,
        logger: ClicktLogger,
    ) {
        parsedConfiguration?.validate(logger)
        if (outputTypes.count { it != ParsedConfiguration.OutputType.CONSOLE } > 1 && outputSink != null) {
            logger.warn("Output path is ignored when multiple output types are specified. Use --output-dir and --output-base-name instead.")
        }
        if (policyPluginDir != null && !BuildConfig.CAN_USE_PLUGINS) {
            logger.error("Policy plugins are not supported on this platform and will be ignored.")
        }
    }

    private suspend fun loadGradleVersion(parsedConfiguration: ParsedConfiguration?): String? {
        return withContext(ioDispatcher) {
            val gradleWrapperPropertiesPath = gradleWrapperPropertiesPath
                ?: parsedConfiguration?.gradleWrapperPropertiesPath
                ?: DEFAULT_GRADLE_PROPERTIES_PATH
            if (!fileSystem.exists(gradleWrapperPropertiesPath)) return@withContext null
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

    private fun outputFormatters(configuration: ParsedConfiguration?): List<Formatter> {
        val outputTypes = outputTypes.takeUnless { it.isEmpty() }
            ?: configuration?.outputTypes
            ?: configuration?.outputType?.let(::listOf)
            ?: listOf(ParsedConfiguration.OutputType.CONSOLE)
        return outputTypes.map { outputType ->
            when (outputType) {
                ParsedConfiguration.OutputType.CONSOLE ->
                    Formatter.Console(ConsoleFormatter(CliktConsolePrinter()))

                ParsedConfiguration.OutputType.HTML ->
                    Formatter.Html(HtmlFormatter(ioDispatcher = ioDispatcher))

                ParsedConfiguration.OutputType.MARKDOWN ->
                    Formatter.Markdown(MarkdownFormatter(ioDispatcher = ioDispatcher))

                ParsedConfiguration.OutputType.JSON ->
                    Formatter.Json(JsonFormatter(ioDispatcher = ioDispatcher))
            }
        }
    }

    private fun createOuputSink(
        dir: Path,
        baseName: String,
        outputType: ParsedConfiguration.OutputType,
        formatterTypes: List<ParsedConfiguration.OutputType>,
    ): OutputSink {
        val optionOutputSink = outputSink
        val hasSingleNonConsoleOutput =
            formatterTypes.count { it != ParsedConfiguration.OutputType.CONSOLE } == 1
        return when {
            // Standard output, used by all formatters
            optionOutputSink is OutputSink.System -> optionOutputSink

            // Only a single non-console output, so use output sink directly
            optionOutputSink != null && hasSingleNonConsoleOutput -> optionOutputSink

            // Otherwise, use outputDir and outputBaseName
            else -> {
                val extension = when (outputType) {
                    ParsedConfiguration.OutputType.HTML -> "html"
                    ParsedConfiguration.OutputType.MARKDOWN -> "md"
                    ParsedConfiguration.OutputType.JSON -> "json"
                    else -> error("Unsupported output type: $outputType")
                }
                fileSystem.createDirectories(dir)
                val outputPath = dir / "$baseName.$extension"
                OutputSink.File(fileSystem.sink(outputPath), outputPath)
            }
        }
    }

    private suspend fun format(
        input: Input,
        formatters: List<Formatter>,
        configuration: ParsedConfiguration?,
    ) {
        val outputDir = multiplePathOptions?.outputDir
            ?: configuration?.outputDir
            ?: DEFAULT_OUTPUT_DIR.toPath()
        val outputBaseName = multiplePathOptions?.outputBaseName
            ?: configuration?.outputBaseName
            ?: DEFAULT_OUTPUT_BASE_NAME
        val formatterTypes = formatters.map { it.outputType }

        for (formatter in formatters) {
            when (formatter) {
                is Formatter.Console -> formatter.format(input)
                is Formatter.Sink -> {
                    val sink = createOuputSink(
                        dir = outputDir,
                        baseName = outputBaseName,
                        outputType = formatter.outputType,
                        formatterTypes = formatterTypes,
                    )
                    formatter.format(input, sink)
                    if (logLevel >= LogLevel.DEFAULT && sink is OutputSink.File) {
                        echo("Report generated at ${sink.path}")
                    }
                }
            }
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
            if (throwable == null) {
                echo(message, err = true)
            } else {
                echo(
                    message = buildString {
                        append(message)
                        if (logLevel >= LogLevel.DEBUG) {
                            appendLine()
                            append(throwable.stackTraceToString())
                        } else {
                            append(": ${throwable.message}")
                        }
                    },
                    err = true
                )
            }
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

    private class ReplaceNotAvailableException : PrintMessage(
        statusCode = 1,
        message = "Replace option cannot be used if multiple version catalogs are provided, or if checking non-static versions",
        printError = true
    )

    companion object Companion {
        private const val CONSOLE_TYPE = "console"
        private const val HTML_TYPE = "html"
        private const val MARKDOWN_TYPE = "markdown"
        private const val JSON_TYPE = "json"
        private val GRADLE_URL_REGEX =
            Regex("https://services.gradle.org/distributions/gradle-(.*)-.*.zip")

        private val DEFAULT_GRADLE_PROPERTIES_PATH =
            "gradle/wrapper/gradle-wrapper.properties".toPath()
    }
}

private class ReleaseNoteOptions : OptionGroup() {
    val searchReleaseNote by option(
        "--search-release-notes",
        help = "Search for release notes for updated versions on GitHub",
    ).nullableFlag().required()

    val githubToken by option(
        "--github-token",
        help = "GitHub token for searching release notes. Taken from CAUPAIN_GITHUB_TOKEN environment variable if not set",
    )
}

private class MultiplePathOptions(fileSystem: FileSystem) : OptionGroup() {

    val outputDir by option(
        "--output-dir",
        help = "Report output dir. Only used if multiple output types are specified, and output is not set to standard output",
        helpTags = mapOf(HelpFormatter.Tags.DEFAULT to DEFAULT_OUTPUT_DIR)
    )
        .path(canBeFile = false, canBeDir = true, fileSystem = fileSystem)
        .required()

    val outputBaseName by option(
        "--output-base-name",
        help = "Report output base name, without extension. Only used if multiple output types are specified, and output is not set to standard output",
    ).default(DEFAULT_OUTPUT_BASE_NAME)
}

internal const val DEFAULT_OUTPUT_DIR = "build/reports"
internal const val DEFAULT_OUTPUT_BASE_NAME = "dependencies-update"
