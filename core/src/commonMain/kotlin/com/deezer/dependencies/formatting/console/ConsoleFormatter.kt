package com.deezer.dependencies.formatting.console

import com.deezer.dependencies.formatting.Formatter
import com.deezer.dependencies.model.UpdateInfo

/**
 * ConsoleFormatter is a [Formatter] that formats the output for the console.
 */
public class ConsoleFormatter(
    private val consolePrinter: ConsolePrinter
) : Formatter {
    override suspend fun format(updates: Map<UpdateInfo.Type, List<UpdateInfo>>) {
        if (updates.isEmpty() || updates.values.all { it.isEmpty() }) {
            consolePrinter.print(NO_UPDATES)
        } else {
            consolePrinter.print(UPDATES_TITLE)
            printUpdates(LIBRARY_TITLE, updates[UpdateInfo.Type.LIBRARY].orEmpty())
            printUpdates(PLUGIN_TITLE, updates[UpdateInfo.Type.PLUGIN].orEmpty())
        }
    }

    private fun printUpdates(title: String, updates: List<UpdateInfo>) {
        if (updates.isNotEmpty()) {
            consolePrinter.print(title)
            for (update in updates) {
                consolePrinter.print(
                    "- ${update.dependencyId}: ${update.currentVersion} -> ${update.updatedVersion}"
                )
            }
        }
    }

    internal companion object {
        const val NO_UPDATES = "No updates available."
        const val UPDATES_TITLE = "Updates are available"
        const val LIBRARY_TITLE = "Library updates:"
        const val PLUGIN_TITLE = "Plugin updates:"
    }
}