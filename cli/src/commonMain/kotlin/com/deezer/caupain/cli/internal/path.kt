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

package com.deezer.caupain.cli.internal

import com.github.ajalt.clikt.completion.CompletionCandidates
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.ProcessedArgument
import com.github.ajalt.clikt.parameters.arguments.RawArgument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.options.NullableOption
import com.github.ajalt.clikt.parameters.options.RawOption
import com.github.ajalt.clikt.parameters.options.convert
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM

private fun pathType(context: Context, fileOkay: Boolean, folderOkay: Boolean): String = when {
    fileOkay && !folderOkay -> context.localization.pathTypeFile()
    !fileOkay && folderOkay -> context.localization.pathTypeDirectory()
    else -> context.localization.pathTypeOther()
}

private fun convertToPath(
    stringPath: String,
    mustExist: Boolean,
    canBeFile: Boolean,
    canBeFolder: Boolean,
    canBeSymlink: Boolean,
    fileSystem: FileSystem,
    context: Context,
    fail: (String) -> Unit,
): Path {
    val name = pathType(context, canBeFile, canBeFolder)
    return with(context.localization) {
        stringPath.toPath().also { path ->
            val metadata = fileSystem.metadataOrNull(path)
            when {
                mustExist && !fileSystem.exists(path) ->
                    fail(pathDoesNotExist(name, path.toString()))

                !canBeFile && metadata?.isRegularFile == true ->
                    fail(pathIsFile(name, path.toString()))

                !canBeFolder && metadata?.isDirectory == true ->
                    fail(pathIsDirectory(name, path.toString()))

                !canBeSymlink && metadata?.symlinkTarget != null ->
                    fail(pathIsSymlink(name, path.toString()))
            }
        }
    }
}

internal fun RawArgument.path(
    mustExist: Boolean = false,
    canBeFile: Boolean = true,
    canBeDir: Boolean = true,
    canBeSymlink: Boolean = true,
    fileSystem: FileSystem = FileSystem.SYSTEM,
): ProcessedArgument<Path, Path> {
    return convert(completionCandidates = CompletionCandidates.Path) { str ->
        convertToPath(
            str,
            mustExist,
            canBeFile,
            canBeDir,
            canBeSymlink,
            fileSystem,
            context
        ) { fail(it) }
    }
}

fun RawOption.path(
    mustExist: Boolean = false,
    canBeFile: Boolean = true,
    canBeDir: Boolean = true,
    canBeSymlink: Boolean = true,
    fileSystem: FileSystem = FileSystem.SYSTEM,
): NullableOption<Path, Path> {
    return convert({ localization.pathMetavar() }, CompletionCandidates.Path) { str ->
        convertToPath(
            str,
            mustExist,
            canBeFile,
            canBeDir,
            canBeSymlink,
            fileSystem,
            context
        ) { fail(it) }
    }
}