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

package com.deezer.caupain.formatting.html

import com.deezer.caupain.formatting.FileFormatter
import com.deezer.caupain.formatting.Formatter
import com.deezer.caupain.formatting.model.Input
import com.deezer.caupain.formatting.model.VersionReferenceInfo
import com.deezer.caupain.internal.asAppendable
import com.deezer.caupain.model.GradleDependencyVersion
import com.deezer.caupain.model.GradleUpdateInfo
import com.deezer.caupain.model.SelfUpdateInfo
import com.deezer.caupain.model.UpdateInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.html.FlowContent
import kotlinx.html.UL
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.br
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.id
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.stream.appendHTML
import kotlinx.html.style
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.tr
import kotlinx.html.ul
import kotlinx.html.unsafe
import okio.BufferedSink
import okio.FileSystem
import okio.Path
import okio.SYSTEM

/**
 * HtmlFormatter is a [Formatter] that formats the output as HTML.
 *
 * @param path The path to the HTML file to write.
 * @param fileSystem The file system to use for writing the file. Default uses the native file system.
 * @param ioDispatcher The coroutine dispatcher to use for IO operations. Default is [Dispatchers.IO].
 */
public class HtmlFormatter(
    path: Path,
    fileSystem: FileSystem = FileSystem.SYSTEM,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : FileFormatter(path, fileSystem, ioDispatcher) {

    override suspend fun BufferedSink.writeUpdates(input: Input) {
        this
            .asAppendable()
            .appendHTML()
            .html {
                head {
                    style {
                        unsafe {
                            raw(STYLE)
                        }
                    }
                }
                body {
                    if (input.isEmpty) {
                        h1 { +"No updates available." }
                    } else {
                        h1 { +"Dependency updates" }
                        appendSelfUpdate(input.selfUpdateInfo)
                        appendGradleUpdate(input.gradleUpdateInfo)
                        appendVersionReferenceUpdates(input.versionReferenceInfo)
                        for ((type, currentUpdates) in input.updateInfos) {
                            appendDependencyUpdates(type, currentUpdates)
                        }
                        appendIgnoredUpdates(input.ignoredUpdateInfos)
                    }
                }
            }
    }

    private fun FlowContent.appendSelfUpdate(selfUpdateInfo: SelfUpdateInfo?) {
        if (selfUpdateInfo == null) return
        h2 { +"Self Update" }
        p {
            +"Caupain current version is ${selfUpdateInfo.currentVersion} whereas last version is ${selfUpdateInfo.updatedVersion}."
            br
            +"You can update Caupain via"
            if (selfUpdateInfo.sources.size == 1) {
                +" "
                val source = selfUpdateInfo.sources.single()
                if (source.link == null) {
                    +source.description
                } else {
                    a(href = source.link) { +source.description }
                }
            } else {
                +" :"
                ul {
                    for (source in selfUpdateInfo.sources) {
                        li {
                            if (source.link == null) {
                                +source.description
                            } else {
                                a(href = source.link) { +source.description }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun FlowContent.appendGradleUpdate(updateInfo: GradleUpdateInfo?) {
        if (updateInfo == null) return
        h2 { +"Gradle" }
        p {
            +"Gradle current version is ${updateInfo.currentVersion} whereas last version is ${updateInfo.updatedVersion}."
            +" See "
            a(href = updateInfo.url) { +"release note" }
            +"."
        }
    }

    private fun getUpdateKey(type: UpdateInfo.Type, id: String): String {
        return "update_${type}_${id}"
    }

    private fun FlowContent.appendVersionReferenceUpdates(updates: List<VersionReferenceInfo>?) {
        if (updates.isNullOrEmpty()) return
        h2 { +"Version References" }
        p {
            table {
                tr {
                    th { +ID_TITLE }
                    th { +CURRENT_VERSION_TITLE }
                    th { +UPDATED_VERSION_TITLE }
                    th { +"Details" }
                }
                for (update in updates) {
                    tr {
                        td { +update.id }
                        td { +update.currentVersion.toString() }
                        td { +update.updatedVersion.toString() }
                        td { appendVersionReferencesDetails(update) }
                    }
                }
            }
        }
    }

    private fun FlowContent.appendVersionReferencesDetails(update: VersionReferenceInfo) {
        var hasContent = false
        if (update.fullyUpdatedLibraries.isNotEmpty()) {
            hasContent = true
            +"Libraries: "
            update.fullyUpdatedLibraries.forEachIndexed { index, key ->
                if (index > 0) +", "
                a(href = "#${getUpdateKey(UpdateInfo.Type.LIBRARY, key)}") {
                    +key
                }
            }
        }
        if (update.fullyUpdatedPlugins.isNotEmpty()) {
            if (hasContent) br
            hasContent = true
            +"Plugins: "
            update.fullyUpdatedPlugins.forEachIndexed { index, key ->
                if (index > 0) +", "
                a(href = "#${getUpdateKey(UpdateInfo.Type.PLUGIN, key)}") {
                    +key
                }
            }
        }
        if (!update.isFullyUpdated) {
            if (hasContent) br
            +"Updates for these dependency using the reference were not found for the updated version:"
            ul {
                appendIncompletelyUpdatedDependencies(
                    type = UpdateInfo.Type.LIBRARY,
                    updatedVersion = update.updatedVersion,
                    keys = update.libraryKeys,
                    updates = update.updatedLibraries
                )
                appendIncompletelyUpdatedDependencies(
                    type = UpdateInfo.Type.PLUGIN,
                    updatedVersion = update.updatedVersion,
                    keys = update.pluginKeys,
                    updates = update.updatedPlugins
                )
            }
        }
    }

    private fun UL.appendIncompletelyUpdatedDependencies(
        type: UpdateInfo.Type,
        updatedVersion: GradleDependencyVersion.Static,
        keys: List<String>,
        updates: Map<String, GradleDependencyVersion.Static>,
    ) {
        for (key in keys) {
            val cUpdatedVersion = updates[key]
            if (cUpdatedVersion != updatedVersion) {
                li {
                    if (cUpdatedVersion == null) {
                        +key
                    } else {
                        a(href = "#${getUpdateKey(type, key)}") {
                            +key
                        }
                    }
                    +": "
                    +(cUpdatedVersion?.toString() ?: "(no update found)")
                }
            }
        }
    }

    private fun FlowContent.appendDependencyUpdates(
        type: UpdateInfo.Type,
        updates: List<UpdateInfo>
    ) {
        if (updates.isEmpty()) return
        h2 { +type.title }
        p {
            table {
                tr {
                    th { +ID_TITLE }
                    th { +"Name" }
                    th { +CURRENT_VERSION_TITLE }
                    th { +UPDATED_VERSION_TITLE }
                    th { +"URL" }
                }
                for (update in updates) {
                    tr {
                        id = getUpdateKey(type, update.dependency)
                        td { +update.dependencyId }
                        td { +update.name.orEmpty() }
                        td { +update.currentVersion.toString() }
                        td { +update.updatedVersion.toString() }
                        td {
                            update
                                .url
                                ?.let { url ->
                                    a(href = url) { +url }
                                }
                                ?: +""
                        }
                    }
                }
            }
        }
    }

    private fun FlowContent.appendIgnoredUpdates(updates: List<UpdateInfo>) {
        if (updates.isEmpty()) return
        h2 { +"Ignored" }
        p {
            table {
                tr {
                    th { +ID_TITLE }
                    th { +CURRENT_VERSION_TITLE }
                    th { +UPDATED_VERSION_TITLE }
                }
                for (update in updates) {
                    tr {
                        td { +update.dependencyId }
                        td { +update.currentVersion.toString() }
                        td { +update.updatedVersion.toString() }
                    }
                }
            }
        }
    }

    private companion object {
        private const val ID_TITLE = "Id"
        private const val CURRENT_VERSION_TITLE = "Current version"
        private const val UPDATED_VERSION_TITLE = "Updated version"

        private const val STYLE = """
        body {
          background-color: Canvas;
          color: CanvasText;
          color-scheme: light dark;
        }
            
        th,
        td {
          border: 1px solid ButtonBorder;
          padding: 8px 10px;
        }
        
        td {
          text-align: center;
        }
        
        tr:nth-of-type(even) {
          background-color: ButtonFace;
        }
        
        table {
          border-collapse: collapse;
          border: 2px solid ButtonBorder;
          width: 100%;
        }  
        """
    }
}