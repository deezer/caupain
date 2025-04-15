package com.deezer.dependencies.formatting.html

import com.deezer.dependencies.internal.asAppendable
import com.deezer.dependencies.formatting.Formatter
import com.deezer.dependencies.model.UpdateInfo
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
import okio.BufferedSink

public class HtmlFormatter(
    private val sink: BufferedSink
) : Formatter {
    override fun format(updates: List<UpdateInfo>) {
        sink
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
                        val updatesByType = mutableMapOf<UpdateInfo.Type, MutableList<UpdateInfo>>()
                        for (update in updates) {
                            val type = update.type
                            val list = updatesByType[type]
                                ?: mutableListOf<UpdateInfo>().also { updatesByType[type] = it }
                            list.add(update)
                        }
                        h1 { +"Dependency updates" }
                        for ((type, currentUpdates) in updatesByType) {
                            appendDependencyUpdates(type, currentUpdates)
                        }
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