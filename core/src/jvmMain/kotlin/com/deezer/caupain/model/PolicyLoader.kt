package com.deezer.caupain.model

import okio.Path
import java.net.URLClassLoader
import java.util.ServiceLoader

internal actual object PolicyLoader {
    actual fun loadPolicies(paths: Iterable<Path>): Iterable<Policy> {
        val childClassLoader = URLClassLoader(
            paths
                .asSequence()
                .map { it.toNioPath().toUri().toURL() }
                .toList()
                .toTypedArray(),
            Policy::class.java.classLoader
        )
        return ServiceLoader.load(Policy::class.java, childClassLoader)
    }
}