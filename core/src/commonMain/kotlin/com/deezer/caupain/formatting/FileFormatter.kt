package com.deezer.caupain.formatting

import com.deezer.caupain.model.DependenciesUpdateResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.BufferedSink
import okio.FileSystem
import okio.Path
import okio.SYSTEM

/**
 * Interface for formatters that write to a file.
 *
 * @param path The path to the HTML file to write.
 * @param fileSystem The file system to use for writing the file. Default uses the native file system.
 * @param ioDispatcher The coroutine dispatcher to use for IO operations. Default is [Dispatchers.IO].
 */
public abstract class FileFormatter(
    protected val path: Path,
    protected val fileSystem: FileSystem = FileSystem.SYSTEM,
    protected val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : Formatter {

    /**
     * The path to the file where the formatted output will be written.
     */
    public open val outputPath: String
        get() = fileSystem.canonicalize(path).toString()

    override suspend fun format(updates: DependenciesUpdateResult) {
        path.parent?.let(fileSystem::createDirectories)
        withContext(ioDispatcher) {
            fileSystem.write(path) {
                writeUpdates(updates)
            }
        }
    }

    /**
     * Writes the formatted output to the given [BufferedSink].
     */
    protected abstract suspend fun BufferedSink.writeUpdates(updates: DependenciesUpdateResult)
}