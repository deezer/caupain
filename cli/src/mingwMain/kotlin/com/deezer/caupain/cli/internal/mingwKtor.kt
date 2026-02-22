package com.deezer.caupain.cli.internal

import platform.windows.SetEnvironmentVariableW

internal actual fun silenceKtorLogging() {
    SetEnvironmentVariableW("KTOR_LOG_LEVEL", "ERROR")
}
