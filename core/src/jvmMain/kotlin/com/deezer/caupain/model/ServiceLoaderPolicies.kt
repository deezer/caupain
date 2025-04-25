package com.deezer.caupain.model

import com.deezer.caupain.internal.catch
import okio.Path
import java.net.URLClassLoader
import java.util.ServiceConfigurationError
import java.util.ServiceLoader

internal actual fun loadPolicies(paths: Iterable<Path>): Iterable<Policy> {
    val childClassLoader = URLClassLoader(
        paths
            .asSequence()
            .map { it.toNioPath().toUri().toURL() }
            .toList()
            .toTypedArray(),
        Policy::class.java.classLoader
    )
    return ServiceLoader
        .load(Policy::class.java, childClassLoader)
        .catch<Policy, ServiceConfigurationError>()
}