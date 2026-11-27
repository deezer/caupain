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

import com.deezer.caupain.config.ConfigurationOverrides
import com.deezer.caupain.config.model.Configuration.OutputType
import com.deezer.caupain.model.gradle.GradleStabilityLevel
import okio.Path
import okio.Path.Companion.toPath

/**
 * The parsed inputs of the action.
 *
 * @property configFile The configuration file, if explicitly set
 * @property overrides Inputs overriding the configuration file
 * @property outputTypes Output types. Empty if not set.
 */
@Suppress("LongParameterList") // Reflects the inputs
internal class ActionInputs(
    val configFile: Path?,
    val overrides: ConfigurationOverrides,
    val gradleWrapperPropertiesPath: Path?,
    val outputTypes: List<OutputType>,
    val outputDir: Path?,
    val outputBaseName: String?,
    val showVersionReferences: Boolean,
    val jobSummary: Boolean,
    val failOnUpdates: Boolean,
)

/**
 * Reads the inputs of the action.
 *
 * @throws InvalidInputException if an input has an invalid value
 */
internal fun ActionEnvironment.readInputs(): ActionInputs {
    return ActionInputs(
        configFile = pathInput("config-file"),
        overrides = ConfigurationOverrides(
            versionCatalogPaths = listInput("version-catalog").map { it.toPath() },
            excludedKeys = listInput("excluded").toSet(),
            policies = input("policy")?.split(",")?.map { it.trim() },
            cacheDir = pathInput("cache-dir"),
            disableCache = booleanInput("no-cache") == true,
            cleanCache = booleanInput("clean-cache") == true,
            gradleStabilityLevel = input("gradle-stability-level")?.toStabilityLevel(),
            searchReleaseNote = booleanInput("search-release-notes"),
            githubToken = input("github-token"),
            verifyExistence = booleanInput("verify-existence") == true,
        ),
        gradleWrapperPropertiesPath = pathInput("gradle-wrapper-properties"),
        outputTypes = listInput("output-types").map { it.toOutputType() }.distinct(),
        outputDir = pathInput("output-dir"),
        outputBaseName = input("output-base-name"),
        showVersionReferences = booleanInput("show-version-references") == true,
        jobSummary = booleanInput("job-summary") ?: true,
        failOnUpdates = booleanInput("fail-on-updates") == true,
    )
}

private fun String.toStabilityLevel(): GradleStabilityLevel {
    return GradleStabilityLevel.fromKey(this) ?: throw InvalidInputException(
        "Input 'gradle-stability-level' must be one of " +
                "${GradleStabilityLevel.entries.joinToString { it.key }}, but was '$this'"
    )
}

private fun String.toOutputType(): OutputType {
    return OutputType.fromKey(this) ?: throw InvalidInputException(
        "Input 'output-types' must only contain " +
                "${OutputType.entries.joinToString { it.key }}, but contained '$this'"
    )
}
