package com.deezer.dependencies.model

import okio.Path

internal actual object PolicyLoader {
    actual fun loadPolicies(paths: Iterable<Path>): Iterable<Policy> = emptyList()
}