/*
 * MIT License
 *
 * Copyright (c) 2026 Deezer
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
 *
 */

package com.deezer.caupain.action

import com.deezer.caupain.CaupainException
import com.deezer.caupain.CorruptedCacheException
import com.deezer.caupain.DependencyUpdateChecker
import com.deezer.caupain.UnavailableCacheException
import com.deezer.caupain.config.ConfigurationParser
import com.deezer.caupain.config.ConfigurationResolver
import com.deezer.caupain.config.model.Configuration.OutputType
import com.deezer.caupain.formatting.SinkFormatter
import com.deezer.caupain.formatting.console.ConsoleFormatter
import com.deezer.caupain.formatting.console.ConsolePrinter
import com.deezer.caupain.formatting.html.HtmlFormatter
import com.deezer.caupain.formatting.json.JsonFormatter
import com.deezer.caupain.formatting.markdown.MarkdownFormatter
import com.deezer.caupain.formatting.model.Input
import com.deezer.caupain.model.Configuration
import com.deezer.caupain.model.DependenciesUpdateResult
import com.deezer.caupain.model.GradleConfiguration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import com.deezer.caupain.config.model.Configuration as ParsedConfiguration

/**
 * Runs Caupain as a GitHub action: reads the inputs, checks for updates, writes the reports, the
 * job summary and the step outputs.
 */
@Suppress("LongParameterList") // Needed for dependency injection
internal class CaupainAction(
    private val environment: ActionEnvironment,
    private val fileSystem: FileSystem,
    private val logger: ActionLogger,
    private val print: (String) -> Unit,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val createUpdateChecker: (
        Configuration,
        GradleConfiguration?,
    ) -> DependencyUpdateChecker = { configuration, gradleConfiguration ->
        DependencyUpdateChecker(
            configuration = configuration,
            gradleConfiguration = gradleConfiguration,
            logger = logger,
            fileSystem = fileSystem,
            ioDispatcher = ioDispatcher,
        )
    },
) {

    private val workflowFiles = WorkflowFiles(fileSystem, environment)

    /**
     * Runs the action.
     *
     * @return the exit code of the action
     */
    @Suppress("TooGenericExceptionCaught") // Anything unexpected must fail the step cleanly
    suspend fun run(): Int {
        return try {
            execute()
        } catch (e: InvalidInputException) {
            logger.error(e.message.orEmpty())
            EXIT_FAILURE
        } catch (_: CorruptedCacheException) {
            logger.error(
                "The cache is corrupted. Try to run again with the clean-cache input set to true"
            )
            EXIT_FAILURE
        } catch (e: UnavailableCacheException) {
            logger.error(
                "Cache dir at ${e.cacheDir} is not available. Check your permissions, or " +
                        "try to specify another cache dir with cache-dir or disable caching " +
                        "with no-cache"
            )
            EXIT_FAILURE
        } catch (e: CaupainException) {
            logger.error(e.message.orEmpty())
            EXIT_FAILURE
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Unexpected error: ${e.message}", e)
            EXIT_FAILURE
        }
    }

    private suspend fun execute(): Int {
        val inputs = environment.readInputs()
        val parsedConfiguration = loadConfiguration(inputs)
        val configuration = ConfigurationResolver.resolveConfiguration(
            parsedConfiguration = parsedConfiguration,
            overrides = inputs.overrides,
            defaultCacheDir = { environment.runnerTemp?.let { it / DEFAULT_CACHE_DIR_NAME } },
        )
        configuration.githubToken?.let(logger::mask)
        if (configuration.policyPluginsDir != null) {
            logger.warn("Policy plugins are not supported by the GitHub action and will be ignored")
        }

        val gradleConfiguration = ConfigurationResolver.loadGradleConfiguration(
            fileSystem = fileSystem,
            path = ConfigurationResolver.resolveGradleWrapperPropertiesPath(
                parsedConfiguration = parsedConfiguration,
                override = inputs.gradleWrapperPropertiesPath,
            )
        )

        val updates = createUpdateChecker(configuration, gradleConfiguration).checkForUpdates()

        val input = Input(
            updateResult = updates,
            showVersionReferences = inputs.showVersionReferences
                    || parsedConfiguration?.showVersionReferences == true
        )
        val reportPaths = writeReports(inputs, parsedConfiguration, input)
        if (inputs.jobSummary) writeJobSummary(input)
        setOutputs(updates, reportPaths)

        return if (inputs.failOnUpdates && updates.updateCount > 0) {
            logger.error("Dependency updates are available")
            EXIT_FAILURE
        } else {
            EXIT_SUCCESS
        }
    }

    private suspend fun loadConfiguration(inputs: ActionInputs): ParsedConfiguration? {
        val path = inputs.configFile ?: DEFAULT_CONFIG_FILE.toPath()
        if (!fileSystem.exists(path)) {
            // Only an explicitly specified configuration file has to exist
            if (inputs.configFile != null) {
                throw InvalidInputException("Configuration file $path does not exist")
            }
            return null
        }
        return withContext(ioDispatcher) {
            ConfigurationParser.parse(fileSystem, path)
        }.also { it.validate(logger) }
    }

    private suspend fun writeReports(
        inputs: ActionInputs,
        parsedConfiguration: ParsedConfiguration?,
        input: Input,
    ): List<Path> {
        val outputTypes = inputs.outputTypes.takeUnless { it.isEmpty() }
            ?: parsedConfiguration?.outputTypes?.toList()
            ?: parsedConfiguration?.outputType?.let(::listOf)
            ?: listOf(OutputType.CONSOLE)
        val outputDir = inputs.outputDir
            ?: parsedConfiguration?.outputDir
            ?: DEFAULT_OUTPUT_DIR.toPath()
        val outputBaseName = inputs.outputBaseName
            ?: parsedConfiguration?.outputBaseName
            ?: DEFAULT_OUTPUT_BASE_NAME

        val reportPaths = mutableListOf<Path>()
        for (outputType in outputTypes) {
            if (outputType == OutputType.CONSOLE) {
                ConsoleFormatter(ActionConsolePrinter()).format(input)
            } else {
                fileSystem.createDirectories(outputDir)
                val extension = checkNotNull(outputType.fileExtension) {
                    "$outputType does not produce files"
                }
                val path = outputDir / "$outputBaseName.$extension"
                createSinkFormatter(outputType).format(input, fileSystem.sink(path))
                logger.lifecycle("Report generated at $path")
                reportPaths += path
            }
        }
        return reportPaths
    }

    private suspend fun writeJobSummary(input: Input) {
        val sink = workflowFiles.openSummarySink()
        if (sink == null) {
            logger.debug("No job summary file available, skipping job summary")
        } else {
            MarkdownFormatter(ioDispatcher).format(input, sink)
        }
    }

    private fun setOutputs(updates: DependenciesUpdateResult, reportPaths: List<Path>) {
        workflowFiles.setOutput("has-updates", (updates.updateCount > 0).toString())
        workflowFiles.setOutput("updates-count", updates.updateCount.toString())
        workflowFiles.setOutput("report-paths", reportPaths.joinToString("\n"))
    }

    private fun createSinkFormatter(outputType: OutputType): SinkFormatter = when (outputType) {
        OutputType.HTML -> HtmlFormatter(ioDispatcher)
        OutputType.MARKDOWN -> MarkdownFormatter(ioDispatcher)
        OutputType.JSON -> JsonFormatter(ioDispatcher)
        OutputType.CONSOLE -> error("Console is not a sink output type")
    }

    private val DependenciesUpdateResult.updateCount: Int
        get() = updateInfos.values.sumOf { it.size } + if (gradleUpdateInfo != null) 1 else 0

    private inner class ActionConsolePrinter : ConsolePrinter {
        override fun print(message: String) {
            this@CaupainAction.print(message)
        }

        override fun printError(message: String) {
            logger.error(message)
        }
    }

    private companion object {
        const val EXIT_SUCCESS = 0
        const val EXIT_FAILURE = 1
        const val DEFAULT_CONFIG_FILE = "caupain.toml"
        const val DEFAULT_CACHE_DIR_NAME = "caupain-cache"
        const val DEFAULT_OUTPUT_DIR = "build/reports"
        const val DEFAULT_OUTPUT_BASE_NAME = "dependencies-update"
    }
}
