package com.deezer.dependencies.internal

import co.touchlab.stately.collections.ConcurrentMutableMap

internal inline fun <K : Any, V : Any> ConcurrentMutableMap<K, V>.computeIfAbsent(
    key: K,
    crossinline compute: (K) -> V
): V = block { map ->
    val present = map[key]
    if (present != null) {
        present
    } else {
        val newValue = compute(key)
        map[key] = newValue
        newValue
    }
}