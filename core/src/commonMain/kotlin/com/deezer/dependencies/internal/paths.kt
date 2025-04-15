package com.deezer.dependencies.internal

import okio.Path

internal val Path.extension: String
    get() = name.substringAfterLast('.', "")