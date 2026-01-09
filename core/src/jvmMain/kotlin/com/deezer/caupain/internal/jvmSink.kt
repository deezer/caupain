package com.deezer.caupain.internal

import okio.Sink
import okio.sink

/**
 * A platform-specific sink writing to the system standard output. This uses [System.out].
 */
public actual fun systemSink(): Sink = System.out.sink()