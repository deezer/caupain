package com.deezer.caupain.internal

import okio.Path

internal val Path.extension: String
    get() = name.substringAfterLast('.', "")