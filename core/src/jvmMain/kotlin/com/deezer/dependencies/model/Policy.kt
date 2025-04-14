package com.deezer.dependencies.model

import java.util.ServiceLoader

internal actual fun loadExternalPolicies(): Iterable<Policy> {
    return ServiceLoader.load(Policy::class.java)
}