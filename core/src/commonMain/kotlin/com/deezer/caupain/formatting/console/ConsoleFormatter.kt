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

import com.deezer.caupain.formatting.model.Input
import com.deezer.caupain.formatting.model.VersionReferenceInfo
import com.deezer.caupain.model.GradleUpdateInfo
import com.deezer.caupain.model.SelfUpdateInfo
import com.deezer.caupain.model.UpdateInfo

/**
 * ConsoleFormatter is a formatter that formats the output for the console.
 */
public class ConsoleFormatter(
    private val consolePrinter: ConsolePrinter
) {
    /**
     * Formats the update result to the console.
     */
    public fun format(input: Input) {
        if (input.isEmpty) {
            consolePrinter.print(NO_UPDATES)
        } else {
            consolePrinter.print(UPDATES_TITLE)
            printSelfUpdate(input.selfUpdateInfo)
            printGradleUpdate(input.gradleUpdateInfo)
            printReferenceUpdates(input.versionReferenceInfo)
            printUpdates(LIBRARY_TITLE, input.updateInfos[UpdateInfo.Type.LIBRARY].orEmpty())
            printUpdates(PLUGIN_TITLE, input.updateInfos[UpdateInfo.Type.PLUGIN].orEmpty())
            printUpdates(IGNORED_TITLE, input.ignoredUpdateInfos)
        }
    }

    private fun printSelfUpdate(selfUpdateInfo: SelfUpdateInfo?) {
        if (selfUpdateInfo != null) {
            consolePrinter.print(
                buildString {
                    append("Caupain can be updated from version ")
                    append(selfUpdateInfo.currentVersion)
                    append(" to version ")
                    append(selfUpdateInfo.updatedVersion)
                    append(" via ")
                    selfUpdateInfo.sources.joinTo(this) { it.description }
                }
            )
        }
    }

    private fun printGradleUpdate(updateInfo: GradleUpdateInfo?) {
        if (updateInfo != null) {
            consolePrinter.print("Gradle: ${updateInfo.currentVersion} -> ${updateInfo.updatedVersion}")
        }
    }

    private fun printReferenceUpdates(updates: List<VersionReferenceInfo>?) {
        if (!updates.isNullOrEmpty()) {
            consolePrinter.print(VERSIONS_TITLE)
            for (update in updates) {
                consolePrinter.print(
                    buildString {
                        append("- ")
                        append(update.id)
                        append(": ")
                        append(update.currentVersion)
                        append(" -> ")
                        append(update.updatedVersion)
                        if (!update.isFullyUpdated) {
                            var hasContent = false
                            if (update.libraryKeys.isNotEmpty()) {
                                hasContent = true
                                append(" (")
                                append(update.nbFullyUpdatedLibraries)
                                append('/')
                                append(update.libraryKeys.size)
                                append(" libraries updated")
                            } else {
                                append(" (")
                            }
                            if (update.pluginKeys.isNotEmpty()) {
                                if (hasContent) append(", ")
                                append(update.nbFullyUpdatedPlugins)
                                append('/')
                                append(update.libraryKeys.size)
                                append(" plugins updated)")
                            } else {
                                append(')')
                            }
                        }
                    }
                )
            }
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
        const val VERSIONS_TITLE = "Versions updates:"
        const val IGNORED_TITLE = "Ignored updates:"
    }
}