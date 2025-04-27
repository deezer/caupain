package com.deezer.caupain.plugin

import org.gradle.api.Action
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.mapProperty
import org.gradle.kotlin.dsl.property
import java.io.Serializable
import javax.inject.Inject

abstract class OutputsHandler @Inject constructor(
    private val objects: ObjectFactory,
    private val layout: ProjectLayout
) {
    private val outputHandlers = objects.mapProperty<Type, OutputHandler>()

    private val defaultOutputPaths = Type.FILE.associateWith { it.defaultOutputPath(layout) }

    val outputs: Provider<Map<Type, Output>> = outputHandlers.map { specifiedHandlers ->
        buildMap {
            for (type in Type.ALL) {
                val handler = specifiedHandlers[type]
                val enabled = handler?.enabled?.get() ?: type.enabledByDefault
                if (enabled) {
                    val outputFile = when (type) {
                        is Type.Console -> {
                            put(type, Output.Console)
                            continue
                        }
                        is Type.File -> (handler as? FileOutputHandler)
                            ?.outputFile
                            ?: defaultOutputPaths[type]!!
                    }
                    put(type, Output.File(outputFile))
                }
            }
        }
    }

    fun console(action: Action<ConsoleOutputHandler>) {
        val handler = ConsoleOutputHandler(objects)
        action.execute(handler)
        outputHandlers.put(Type.Console, handler)
    }

    fun html(action: Action<FileOutputHandler>) {
        val handler = FileOutputHandler(Type.Html, objects, layout)
        action.execute(handler)
        outputHandlers.put(Type.Html, handler)
    }

    fun markdown(action: Action<FileOutputHandler>) {
        val handler = FileOutputHandler(Type.Markdown, objects, layout)
        action.execute(handler)
        outputHandlers.put(Type.Markdown, handler)
    }

    sealed interface Output : Serializable {
        object Console : Output {
            private fun readResolve(): Any = Console

            override fun toString(): String = "Console"
        }

        data class File(val file: Provider<RegularFile>) : Output {
            override fun toString(): String = "File(file=${file.get().asFile.canonicalPath})"
        }
    }

    sealed class Type(val enabledByDefault: Boolean) {
        object Console : Type(true)

        sealed class File(enabledByDefault: Boolean, private val extension: String) :
            Type(enabledByDefault) {
            fun defaultOutputPath(layout: ProjectLayout): Provider<RegularFile> =
                layout.buildDirectory.file("reports/dependency-updates.$extension")
        }

        object Html : File(true, "html")

        object Markdown : File(false, "md")

        companion object {
            val ALL = listOf(Console, Html, Markdown)
            val FILE = listOf(Html, Markdown)
        }
    }
}

sealed interface OutputHandler {
    val enabled: Property<Boolean>
}

open class ConsoleOutputHandler(objects: ObjectFactory) : OutputHandler {
    override val enabled: Property<Boolean> = objects.property<Boolean>().convention(true)
}

open class FileOutputHandler(
    type: OutputsHandler.Type.File,
    objects: ObjectFactory,
    layout: ProjectLayout
) : OutputHandler {
    override val enabled: Property<Boolean> =
        objects.property<Boolean>().convention(type.enabledByDefault)

    val outputFile: RegularFileProperty =
        objects.fileProperty().convention(type.defaultOutputPath(layout))
}