package com.deezer.dependencies.formatting.console

import com.deezer.dependencies.formatting.Formatter
import com.deezer.dependencies.model.UpdateInfo

public class ConsoleFormatter(
    private val consolePrinter: ConsolePrinter
) : Formatter {
    override suspend fun format(updates: List<UpdateInfo>) {
        if (updates.isEmpty()) {
            consolePrinter.print("No updates available.")
        } else {
            consolePrinter.print("Updates are available")
            for (update in updates) {
                val header = when (update.type) {
                    UpdateInfo.Type.PLUGIN -> "Plugin"
                    UpdateInfo.Type.LIBRARY -> "Library"
                }
                consolePrinter.print(
                    "- $header ${update.dependencyId}: ${update.currentVersion} -> ${update.updatedVersion}"
                )
            }
        }
    }
}