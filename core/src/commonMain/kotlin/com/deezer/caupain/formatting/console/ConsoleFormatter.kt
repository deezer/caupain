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