@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.deezer.dependencies.model

import okio.Path

internal actual object PolicyLoader {
    actual fun loadPolicies(paths: Iterable<Path>): Iterable<Policy> = emptyList()
}