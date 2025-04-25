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