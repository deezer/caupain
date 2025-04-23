package com.deezer.caupain.formatting.html

import com.deezer.caupain.formatting.FileFormatter
import com.deezer.caupain.formatting.Formatter
import com.deezer.caupain.internal.asAppendable
import com.deezer.caupain.model.DependenciesUpdateResult
import com.deezer.caupain.model.GradleUpdateInfo
import com.deezer.caupain.model.UpdateInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.p
import kotlinx.html.stream.appendHTML
import kotlinx.html.style
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.tr
import kotlinx.html.unsafe
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
    private val path: Path,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : FileFormatter {

    override val outputPath: String
        get() = fileSystem.canonicalize(path).toString()

    override suspend fun format(updates: DependenciesUpdateResult) {
        withContext(ioDispatcher) {
            fileSystem.write(path) {
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
                            if (updates.isEmpty()) {
                                h1 { +"No updates available." }
                            } else {
                                h1 { +"Dependency updates" }
                                appendGradleUpdate(updates.gradleUpdateInfo)
                                for ((type, currentUpdates) in updates.updateInfos) {
                                    appendDependencyUpdates(type, currentUpdates)
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

    private fun FlowContent.appendDependencyUpdates(
        type: UpdateInfo.Type,
        updates: List<UpdateInfo>
    ) {
        if (updates.isEmpty()) return
        h2 { +type.title }
        p {
            table {
                tr {
                    th { +"Id" }
                    th { +"Name" }
                    th { +"Current version" }
                    th { +"Updated version" }
                    th { +"URL" }
                }
                for (update in updates) {
                    tr {
                        td { +update.dependencyId }
                        td { +update.name.orEmpty() }
                        td { +update.currentVersion }
                        td { +update.updatedVersion }
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

    private companion object {
        private const val STYLE = """
        th,
        td {
          border: 1px solid rgb(160 160 160);
          padding: 8px 10px;
        }
        
        td {
          text-align: center;
        }
        
        tr:nth-of-type(even) {
          background-color: #eee;
        }
        
        table {
          border-collapse: collapse;
          border: 2px solid rgb(140 140 140);
          width: 100%;
        }  
        """
    }
}