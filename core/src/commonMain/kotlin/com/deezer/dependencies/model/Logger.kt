package com.deezer.dependencies.model

import io.ktor.client.plugins.logging.Logger as KtorLogger

public interface Logger : KtorLogger {

    public fun debug(message: String)

    public fun info(message: String)

    public fun warn(message: String, throwable: Throwable? = null)

    public fun error(message: String, throwable: Throwable? = null)

    override fun log(message: String) {
        debug(message)
    }

    public companion object {
        public val EMPTY: Logger = object : Logger {
            override fun debug(message: String) {
                // Nothing to do
            }

            override fun info(message: String) {
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