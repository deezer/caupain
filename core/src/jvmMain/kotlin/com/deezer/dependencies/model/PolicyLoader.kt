@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.deezer.dependencies.model

import okio.Path
import java.io.File
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
            javaClass.classLoader
        )
        val a = File("toto").extension
        return ServiceLoader.load(Policy::class.java, childClassLoader)
    }
}