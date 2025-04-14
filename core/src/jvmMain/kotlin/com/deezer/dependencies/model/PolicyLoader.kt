@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.deezer.dependencies.model

import java.util.ServiceLoader

internal actual object PolicyLoader {
    actual fun loadPolicies(): Iterable<Policy> = ServiceLoader.load(Policy::class.java)
}