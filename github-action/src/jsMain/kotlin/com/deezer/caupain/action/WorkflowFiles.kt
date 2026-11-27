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

import okio.FileSystem
import okio.Sink
import okio.buffer
import okio.use
import kotlin.random.Random

/**
 * Writes to the files provided by the runner to communicate with the workflow: step outputs and
 * job summary. When running outside of GitHub Actions these files are not set, and everything here
 * is a no-op.
 */
internal class WorkflowFiles(
    private val fileSystem: FileSystem,
    private val environment: ActionEnvironment,
) {

    /**
     * Whether a job summary can be written.
     */
    val hasSummary: Boolean
        get() = environment.stepSummaryFile != null

    /**
     * Sets the step output called [name]. Values can span multiple lines.
     */
    fun setOutput(name: String, value: String) {
        val file = environment.outputFile ?: return
        val delimiter = "caupain_${Random.nextLong().toString(RADIX).removePrefix("-")}"
        fileSystem.appendingSink(file).buffer().use { sink ->
            sink.writeUtf8("$name<<$delimiter\n$value\n$delimiter\n")
        }
    }

    /**
     * Opens a sink appending to the job summary, or returns null if there is no job summary.
     */
    fun openSummarySink(): Sink? {
        val file = environment.stepSummaryFile ?: return null
        return fileSystem.appendingSink(file)
    }

    private companion object {
        const val RADIX = 16
    }
}
