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

import okio.Path
import okio.Path.Companion.toPath

/**
 * Thrown when an action input has an invalid value.
 */
internal class InvalidInputException(message: String) : Exception(message)

/**
 * Access to the environment variables set by the GitHub Actions runner, and to the inputs of the
 * action, which are passed as `INPUT_<NAME>` environment variables.
 *
 * The runner upper-cases input names and replaces spaces by underscores, but keeps hyphens. As
 * composite actions have to pass inputs explicitly (and hyphens in environment variable names are
 * not portable), the variant with underscores instead of hyphens is also accepted.
 */
internal class ActionEnvironment(private val variables: Map<String, String?>) {

    /**
     * Path of the file used to set step outputs, if any.
     */
    val outputFile: Path?
        get() = path("GITHUB_OUTPUT")

    /**
     * Path of the file used to write the job summary, if any.
     */
    val stepSummaryFile: Path?
        get() = path("GITHUB_STEP_SUMMARY")

    /**
     * Path of the temporary directory of the runner, which is cleaned after each job.
     */
    val runnerTemp: Path?
        get() = path("RUNNER_TEMP")

    /**
     * The value of the input called [name], or null if the input is not set or blank.
     */
    fun input(name: String): String? {
        val key = name.uppercase().replace(' ', '_')
        val value = variables["INPUT_$key"] ?: variables["INPUT_${key.replace('-', '_')}"]
        return value?.trim()?.takeUnless { it.isEmpty() }
    }

    /**
     * The value of the boolean input called [name], or null if the input is not set or is neither
     * `true` nor `false` (ignoring case).
     */
    fun booleanInput(name: String): Boolean? = input(name)?.lowercase()?.toBooleanStrictOrNull()

    /**
     * The values of the multiline input called [name]: one value per line, blank lines ignored.
     */
    fun listInput(name: String): List<String> {
        return input(name)
            ?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toList()
            .orEmpty()
    }

    /**
     * The value of the path input called [name], or null if the input is not set.
     */
    fun pathInput(name: String): Path? = input(name)?.toPath()

    private fun path(variable: String): Path? =
        variables[variable]?.takeUnless { it.isBlank() }?.toPath()
}
