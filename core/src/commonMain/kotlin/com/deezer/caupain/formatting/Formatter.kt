package com.deezer.caupain.formatting

import com.deezer.caupain.formatting.console.ConsoleFormatter
import com.deezer.caupain.formatting.html.HtmlFormatter
import com.deezer.caupain.model.DependenciesUpdateResult

/**
 * Formatter is an interface for formatting dependency updates.
 *
 * @see [ConsoleFormatter]
 * @see [HtmlFormatter]
 */
public fun interface Formatter {
    public suspend fun format(updates: DependenciesUpdateResult)
}

