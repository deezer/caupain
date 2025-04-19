package com.deezer.dependencies.formatting

import com.deezer.dependencies.model.UpdateInfo
import com.deezer.dependencies.formatting.console.ConsoleFormatter
import com.deezer.dependencies.formatting.html.HtmlFormatter

/**
 * Formatter is an interface for formatting dependency updates.
 *
 * @see [ConsoleFormatter]
 * @see [HtmlFormatter]
 */
public fun interface Formatter {
    public suspend fun format(updates: Map<UpdateInfo.Type, List<UpdateInfo>>)
}