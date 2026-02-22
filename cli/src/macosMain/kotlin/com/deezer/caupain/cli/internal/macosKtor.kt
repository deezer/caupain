package com.deezer.caupain.cli.internal

import platform.posix.setenv

internal actual fun silenceKtorLogging() {
    setenv("KTOR_LOG_LEVEL", "ERROR", 1)
}
