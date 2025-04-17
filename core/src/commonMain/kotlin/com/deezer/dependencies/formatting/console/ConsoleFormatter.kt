package com.deezer.dependencies.formatting.console

import com.deezer.dependencies.formatting.Formatter
import com.deezer.dependencies.model.UpdateInfo

public class ConsoleFormatter(
    private val consolePrinter: ConsolePrinter
) : Formatter {
    override suspend fun format(updates: Map<UpdateInfo.Type, List<UpdateInfo>>) {
        if (updates.isEmpty() || updates.values.all { it.isEmpty() }) {
            consolePrinter.print("No updates available.")
        } else {
            consolePrinter.print("Updates are available")
            printUpdates("Library updates:", updates[UpdateInfo.Type.LIBRARY].orEmpty())
            printUpdates("Plugin updates:", updates[UpdateInfo.Type.PLUGIN].orEmpty())
        }
    }

    private fun printUpdates(title: String, updates: List<UpdateInfo>) {
        if (updates.isNotEmpty()) {
            consolePrinter.print(title)
            for (update in updates) {
                consolePrinter.print(
                    "${update.dependencyId}: ${update.currentVersion} -> ${update.updatedVersion}"
                )
            }
        }
    }
}