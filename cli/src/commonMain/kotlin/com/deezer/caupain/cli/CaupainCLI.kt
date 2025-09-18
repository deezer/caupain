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

package com.deezer.caupain.cli

import ca.gosyer.appdirs.AppDirs
import com.deezer.caupain.BuildKonfig
import com.deezer.caupain.CaupainException
import com.deezer.caupain.DependencyUpdateChecker
import com.deezer.caupain.DependencyVersionsReplacer
import com.deezer.caupain.cli.internal.CAN_USE_PLUGINS
import com.deezer.caupain.cli.internal.path
import com.deezer.caupain.cli.model.GradleWrapperProperties
import com.deezer.caupain.cli.resolver.CLISelfUpdateResolver
import com.deezer.caupain.cli.serialization.DefaultToml
import com.deezer.caupain.cli.serialization.decodeFromPath
import com.deezer.caupain.cli.serialization.decodeFromProperties
import com.deezer.caupain.formatting.FileFormatter
import com.deezer.caupain.formatting.Formatter
import com.deezer.caupain.formatting.console.ConsoleFormatter
import com.deezer.caupain.formatting.console.ConsolePrinter
import com.deezer.caupain.formatting.html.HtmlFormatter
import com.deezer.caupain.formatting.json.JsonFormatter
import com.deezer.caupain.formatting.markdown.MarkdownFormatter
import com.deezer.caupain.formatting.model.Input
import com.deezer.caupain.model.Configuration
import com.deezer.caupain.model.Logger
import com.deezer.caupain.model.gradle.GradleStabilityLevel
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
import com.github.ajalt.clikt.parameters.options.deprecated
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
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

class CaupainCLI(
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val parseConfiguration: (FileSystem, Path) -> ParsedConfiguration = { fs, path ->
        DefaultToml.decodeFromPath(path, fs)
    },
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
    private val createVersionReplacer: (FileSystem, CoroutineDispatcher, CoroutineDispatcher) -> DependencyVersionsReplacer = { filesystem, ioDispatcher, defaultDispatcher ->
        DependencyVersionsReplacer(
            fileSystem = filesystem,
            ioDispatcher = ioDispatcher,
            defaultDispatcher = defaultDispatcher
        )
    }
) : SuspendingCliktCommand(name = "caupain") {

    private val appDirs = AppDirs {
        appName = "caupain"
    }

    private val versionCatalogPaths by
    option(
        "-i",
        "--version-catalog",
        help = "Version catalog path. Use multiple times to use multiple version catalogs",
        helpTags = mapOf(HelpFormatter.Tags.DEFAULT to "gradle/libs.versions.toml")
    )
        .path(mustExist = true, canBeFile = true, canBeDir = false, fileSystem = fileSystem)
        .multiple(default = listOf("gradle/libs.versions.toml".toPath()))

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

    private val policy by option(
        "-p",
        "--policy",
        help = "Update policy (default: update to the latest version available)"
    )

    private val listPolicies by option("--list-policies", help = "List available policies")
        .flag()

    private val gradleStabilityLevel by option(help = "Gradle stability level")
        .choice(
            "stable" to GradleStabilityLevel.STABLE,
            "rc" to GradleStabilityLevel.RC,
            "milestone" to GradleStabilityLevel.MILESTONE,
            "release-nightly" to GradleStabilityLevel.RELEASE_NIGHTLY,
            "nightly" to GradleStabilityLevel.NIGHTLY
        )
        .default(
            value = GradleStabilityLevel.STABLE,
            defaultForHelp = "stable"
        )

    private val outputType by option("-t", "--output-type", help = "Output type")
        .choice(
            CONSOLE_TYPE to ParsedConfiguration.OutputType.CONSOLE,
            HTML_TYPE to ParsedConfiguration.OutputType.HTML,
            MARKDOWN_TYPE to ParsedConfiguration.OutputType.MARKDOWN,
            JSON_TYPE to ParsedConfiguration.OutputType.JSON
        )
        .default(
            value = ParsedConfiguration.OutputType.CONSOLE,
            defaultForHelp = CONSOLE_TYPE
        )

    private val outputPath by option(
        "-o", "--output",
        help = "Report output path",
        helpTags = mapOf(HelpFormatter.Tags.DEFAULT to "build/reports/dependencies-update.(html|md|json)")
    ).path(canBeFile = true, canBeDir = false, fileSystem = fileSystem)

    private val showVersionReferences by option(help = "Show versions references update summary in the report")
        .flag()

    private val replace by option(
        "--in-place",
        help = "Replace versions in version catalog in place"
    ).flag()

    private val releaseNoteOptions by ReleaseNoteOptions().cooccurring()

    private val cacheDir by option(help = "Cache directory. This is not used if --no-cache is set")
        .path(canBeDir = true, canBeFile = false, fileSystem = fileSystem)
        .default(
            value = appDirs.getUserCacheDir().toPath(),
            defaultForHelp = "user cache dir"
        )

    private val deprecatedDoNotCache by option("--no--cache", help = "Disable HTTP cache", hidden = true)
        .flag()
        .deprecated("use --no-cache instead")

    private val doNotCache by option("--no-cache", help = "Disable HTTP cache")
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
        versionOption(BuildKonfig.VERSION)
    }

    @Suppress("CyclomaticComplexMethod")
    override suspend fun run() {
        val start = timesource.markNow()

        val backgroundScope = CoroutineScope(SupervisorJob() + defaultDispatcher)
        val logger = ClicktLogger()

        val configuration = loadConfiguration()
        configuration?.validate(logger)
        val finalConfiguration = createConfiguration(configuration)
        if (finalConfiguration.policyPluginsDir != null && !CAN_USE_PLUGINS) {
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
                    appendLine("- <no-policy>: Built-in default to update to the latest version available")
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
        } catch (e: CaupainException) {
            echo(e.message, err = true)
            throw Abort()
        }

        val formatter = outputFormatter(configuration)
        if (formatter is ConsoleFormatter) {
            // Clear progress early to avoid sending progress to console while formatting
            progress?.clear()
            backgroundScope.cancel()
        }

        formatter.format(
            Input(
                updateResult = updates,
                showVersionReferences = configuration?.showVersionReferences
                    ?: showVersionReferences
            )
        )

        if (formatter !is ConsoleFormatter) {
            progress?.clear()
            backgroundScope.cancel()
        }

        if (logLevel >= LogLevel.DEFAULT && formatter is FileFormatter) {
            echo("Report generated at ${formatter.outputPath}")
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
            versionCatalogPaths = versionCatalogPaths,
            excludedKeys = excluded.toSet(),
            policyPluginsDir = policyPluginDir,
            policy = policy,
            cacheDir = if (deprecatedDoNotCache || doNotCache) null else cacheDir,
            debugHttpCalls = debugHttpCalls,
            gradleStabilityLevel = gradleStabilityLevel,
            searchReleaseNote = releaseNoteOptions?.searchReleaseNote == true,
            githubToken = releaseNoteOptions?.githubToken
        )
        val mergedConfiguration = parsedConfiguration?.toConfiguration(baseConfiguration)
            ?: baseConfiguration
        // Handle release note options special case
        return if (
            releaseNoteOptions == null
            && parsedConfiguration?.githubToken != null
            && parsedConfiguration.searchReleaseNote == null
        ) {
            mergedConfiguration.withReleaseNotes()
        } else {
            mergedConfiguration
        }
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

            ParsedConfiguration.OutputType.JSON -> JsonFormatter(
                fileSystem = fileSystem,
                ioDispatcher = ioDispatcher,
                path = configuration?.outputPath
                    ?: outputPath
                    ?: "build/reports/dependencies-update.json".toPath()
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
    }
}

private class ReleaseNoteOptions : OptionGroup() {
    val githubToken by option(
        "--github-token",
        help = "GitHub token for searching release notes",
        envvar = "CAUPAIN_GITHUB_TOKEN"
    ).required()

    val searchReleaseNote by option(
        "--search-release-notes",
        help = "Search for release notes for updated versions on GitHub",
    ).flag(defaultForHelp = "true if GitHub token is provided")
}