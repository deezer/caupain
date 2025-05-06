/*
 * MIT License
 *
 * Copyright (c) 2025 Deezer
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.deezer.caupain.plugin

import com.deezer.caupain.plugin.internal.combine
import org.gradle.api.Action
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.property
import java.io.Serializable
import java.util.Optional
import javax.inject.Inject
import kotlin.jvm.optionals.getOrNull

abstract class OutputsHandler @Inject constructor(
    objects: ObjectFactory,
    layout: ProjectLayout
) {
    val console = ConsoleOutputHandler(objects)

    val html = FileOutputHandler(Type.Html, objects, layout)

    val markdown = FileOutputHandler(Type.Markdown, objects, layout)

    val outputs = combine(console.output, html.output, markdown.output) { outputs ->
        outputs.mapNotNull { it.getOrNull() }
    }

    fun console(action: Action<ConsoleOutputHandler>) {
        action.execute(console)
    }

    fun html(action: Action<FileOutputHandler>) {
        action.execute(html)
    }

    fun markdown(action: Action<FileOutputHandler>) {
        action.execute(markdown)
    }

    sealed interface Output : Serializable {
        object Console : Output {
            private fun readResolve(): Any = Console

            override fun toString(): String = "Console"
        }

        sealed interface File : Output {
            val file: Provider<RegularFile>
        }

        data class Html(override val file: Provider<RegularFile>) : File

        data class Markdown(override val file: Provider<RegularFile>) : File
    }

    internal sealed class Type(val enabledByDefault: Boolean) {
        object Console : Type(true)

        sealed class File(enabledByDefault: Boolean, private val extension: String) :
            Type(enabledByDefault) {
            fun defaultOutputPath(layout: ProjectLayout): Provider<RegularFile> =
                layout.buildDirectory.file("reports/dependency-updates.$extension")
        }

        object Html : File(true, "html")

        object Markdown : File(false, "md")
    }
}

sealed interface OutputHandler {
    val enabled: Property<Boolean>
    val output: Provider<Optional<OutputsHandler.Output>>
}

open class ConsoleOutputHandler internal constructor(objects: ObjectFactory) : OutputHandler {
    final override val enabled: Property<Boolean> = objects.property<Boolean>().convention(true)
    override val output: Provider<Optional<OutputsHandler.Output>> = enabled.map { isEnabled ->
        Optional.of<OutputsHandler.Output>(OutputsHandler.Output.Console).filter { isEnabled }
    }
}

open class FileOutputHandler internal constructor(
    type: OutputsHandler.Type.File,
    objects: ObjectFactory,
    layout: ProjectLayout
) : OutputHandler {
    final override val enabled: Property<Boolean> =
        objects.property<Boolean>().convention(type.enabledByDefault)

    val outputFile: RegularFileProperty =
        objects.fileProperty().convention(type.defaultOutputPath(layout))

    override val output: Provider<Optional<OutputsHandler.Output>> = enabled.map { isEnabled ->
        if (isEnabled) {
            Optional.of(
                when (type) {
                    OutputsHandler.Type.Html -> OutputsHandler.Output.Html(outputFile)
                    OutputsHandler.Type.Markdown -> OutputsHandler.Output.Markdown(outputFile)
                }
            )
        } else {
            Optional.empty()
        }
    }
}