package com.deezer.caupain.formatting

import com.deezer.caupain.model.UpdateInfo
import com.deezer.caupain.formatting.console.ConsoleFormatter
import com.deezer.caupain.formatting.html.HtmlFormatter

/**
 * Formatter is an interface for formatting dependency updates.
 *
 * @see [ConsoleFormatter]
 * @see [HtmlFormatter]
 */
public fun interface Formatter {
    public suspend fun format(updates: Map<UpdateInfo.Type, List<UpdateInfo>>)
}

/**
 * Interface for formatters that write to a file.
 */
public interface FileFormatter : Formatter {
    public val outputPath: String
}