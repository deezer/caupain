package com.deezer.caupain.plugin.internal

import okio.Path.Companion.toOkioPath
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile

internal fun RegularFile.toOkioPath() = asFile.toOkioPath()

internal fun Directory.toOkioPath() = asFile.toOkioPath()