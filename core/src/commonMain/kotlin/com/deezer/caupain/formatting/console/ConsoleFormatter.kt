package com.deezer.caupain.formatting.console

import com.deezer.caupain.formatting.Formatter
import com.deezer.caupain.model.DependenciesUpdateResult
import com.deezer.caupain.model.GradleUpdateInfo
import com.deezer.caupain.model.UpdateInfo

/**
 * ConsoleFormatter is a [Formatter] that formats the output for the console.
 */
public class ConsoleFormatter(
    private val consolePrinter: ConsolePrinter
) : Formatter {
    override suspend fun format(updates: DependenciesUpdateResult) {
        if (updates.isEmpty()) {
            consolePrinter.print(NO_UPDATES)
        } else {
            consolePrinter.print(UPDATES_TITLE)
            printGradleUpdate(updates.gradleUpdateInfo)
            printUpdates(LIBRARY_TITLE, updates.updateInfos[UpdateInfo.Type.LIBRARY].orEmpty())
            printUpdates(PLUGIN_TITLE, updates.updateInfos[UpdateInfo.Type.PLUGIN].orEmpty())
        }
    }

    private fun printGradleUpdate(updateInfo: GradleUpdateInfo?) {
        if (updateInfo != null) {
            consolePrinter.print("Gradle: ${updateInfo.currentVersion} -> ${updateInfo.updatedVersion}")
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