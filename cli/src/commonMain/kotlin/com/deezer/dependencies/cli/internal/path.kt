package com.deezer.dependencies.cli.internal

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

@Suppress("LongParameterList")
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