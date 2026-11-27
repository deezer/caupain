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

import com.deezer.caupain.model.Logger

/**
 * A [Logger] that writes [workflow commands](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-commands)
 * understood by the GitHub Actions runner.
 *
 * @param print Prints a line to standard output
 */
internal class ActionLogger(private val print: (String) -> Unit) : Logger {

    override fun debug(message: String) {
        print("::debug::${escapeData(message)}")
    }

    override fun info(message: String) {
        // Only visible when step debug logging is enabled, like the Gradle info level
        debug(message)
    }

    override fun lifecycle(message: String) {
        print(message)
    }

    override fun warn(message: String, throwable: Throwable?) {
        print("::warning::${escapeData(message)}")
        throwable?.let { debug(it.stackTraceToString()) }
    }

    override fun error(message: String, throwable: Throwable?) {
        print("::error::${escapeData(message)}")
        throwable?.let { debug(it.stackTraceToString()) }
    }

    /**
     * Masks [value] in the logs of the rest of the job.
     */
    fun mask(value: String) {
        print("::add-mask::${escapeData(value)}")
    }
}

/**
 * Escapes the message of a workflow command.
 */
internal fun escapeData(value: String): String = value
    .replace("%", "%25")
    .replace("\r", "%0D")
    .replace("\n", "%0A")
