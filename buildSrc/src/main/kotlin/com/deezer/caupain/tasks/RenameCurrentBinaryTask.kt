package com.deezer.caupain.tasks

import com.deezer.caupain.currentArch
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal

@Suppress("LeakingThis") // This is only abstract to be instantiated by Gradle
abstract class RenameCurrentBinaryTask : Copy() {

    @get:Input
    val architecture = project.currentArch

    @get:Internal
    val binDir = project
        .objects
        .directoryProperty()
        .convention(project.layout.buildDirectory.dir("bin"))

    @get:InputFile
    val binaryFile = architecture.flatMap { arch ->
        binDir.map { binDir ->
            binDir.file(arch.filePath)
        }
    }

    init {
        from(binaryFile)
        into(binDir)
        rename { architecture.get().outFileName }
    }
}