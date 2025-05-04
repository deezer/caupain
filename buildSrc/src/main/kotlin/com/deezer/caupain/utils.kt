package com.deezer.caupain

import org.gradle.api.file.CopySpec
import org.gradle.api.tasks.AbstractCopyTask

fun CopySpec.rename(fixedName: String) = rename { fixedName }