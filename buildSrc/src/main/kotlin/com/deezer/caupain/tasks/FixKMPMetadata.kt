package com.deezer.caupain.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction

open class FixKMPMetadata : DefaultTask() {
    @get:Internal
    val compileOutputs = project.objects.fileCollection()

    @get:Input
    val groupId = project.group.toString()

    @get:InputFiles
    val manifestFiles: FileCollection
        get() = compileOutputs.asFileTree.filter { it.isFile && it.name == "manifest" }

    @get:OutputFiles
    val outputFile: FileCollection
        get() = manifestFiles

    @TaskAction
    fun fixUniqueName() {
        manifestFiles.forEach { manifestFile ->
            val content = manifestFile.useLines { lines ->
                lines
                    .filterNot { it.isBlank() }
                    .map { line ->
                        val iEq = line.indexOf('=')
                        require(iEq != -1) {
                            "Metadata manifest file contents invalid. Contains invalid key-value-pair '$line'"
                        }
                        line.substring(0, iEq) to line.substring(iEq + 1)
                    }
                    .toMap(mutableMapOf())
            }
            val old = content["unique_name"] ?: return
            val prefix = "$groupId\\:"
            if (old.startsWith(prefix)) return
            val new = "$prefix$old"
            content["unique_name"] = new

            manifestFile.bufferedWriter().use { writer ->
                for ((key, value) in content) {
                    writer.append(key)
                    writer.append('=')
                    writer.appendLine(value)
                }
            }
        }
    }
}