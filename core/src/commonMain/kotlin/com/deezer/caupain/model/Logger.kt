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

package com.deezer.caupain.model

import io.ktor.client.plugins.logging.Logger as KtorLogger

/**
 * Logger interface for logging messages at different levels.
 */
public interface Logger {

    /**
     * Logs a debug message.
     *
     * @param message The message to log.
     */
    public fun debug(message: String)

    /**
     * Logs an info message.
     *
     * @param message The message to log.
     */
    public fun info(message: String)

    /**
     * Logs a lifecycle message.
     *
     * @param message The message to log.
     */
    public fun lifecycle(message: String)

    /**
     * Logs a warning message.
     *
     * @param message The message to log.
     * @param throwable An optional throwable to log.
     */
    public fun warn(message: String, throwable: Throwable? = null)

    /**
     * Logs an error message.
     *
     * @param message The message to log.
     * @param throwable An optional throwable to log.
     */
    public fun error(message: String, throwable: Throwable? = null)

    public companion object {
        /**
         * A logger that does nothing. This is useful for disabling logging.
         */
        public val EMPTY: Logger = object : Logger {
            override fun debug(message: String) {
                // Nothing to do
            }

            override fun info(message: String) {
                // Nothing to do
            }

            override fun lifecycle(message: String) {
                // Nothing to do
            }

            override fun warn(message: String, throwable: Throwable?) {
                // Nothing to do
            }

            override fun error(message: String, throwable: Throwable?) {
                // Nothing to do
            }
        }
    }
}

internal class KtorLoggerAdapter(private val logger: Logger) : KtorLogger {
    override fun log(message: String) {
        logger.debug(message)
    }
}