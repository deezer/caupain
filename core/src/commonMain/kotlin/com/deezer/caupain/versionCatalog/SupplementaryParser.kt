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

package com.deezer.caupain.versionCatalog

import com.deezer.caupain.antlr.TomlLexer
import com.deezer.caupain.antlr.TomlParser
import com.deezer.caupain.antlr.TomlParserBaseVisitor
import com.deezer.caupain.model.VersionCatalogInfo
import com.deezer.caupain.model.VersionCatalogInfo.VersionPosition
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import org.antlr.v4.kotlinruntime.CharStreams
import org.antlr.v4.kotlinruntime.CommonTokenStream

internal class SupplementaryParser(
    private val fileSystem: FileSystem,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun parse(versionCatalogPath: Path): VersionCatalogInfo {
        val charStream = withContext(ioDispatcher) {
            fileSystem.read(versionCatalogPath) {
                CharStreams.fromString(readUtf8())
            }
        }
        val lexer = TomlLexer(charStream)
        val tokenStream = CommonTokenStream(lexer)
        val parser = TomlParser(tokenStream)
        val visitor = Visitor().apply { visit(parser.document()) }
        return VersionCatalogInfo(
            ignores = VersionCatalogInfo.Ignores(
                refs = visitor.ignoredRefs,
                libraryKeys = visitor.ignoredLibraryKeys,
                pluginKeys = visitor.ignoredPluginKeys
            ),
            positions = VersionCatalogInfo.Positions(
                versionRefsPositions = visitor.versionRefsPositions,
                libraryVersionPositions = visitor.libraryVersionPositions,
                pluginVersionPositions = visitor.pluginVersionPositions
            )
        )
    }

    private class Visitor : TomlParserBaseVisitor<Unit>() {

        val ignoredRefs = mutableSetOf<String>()
        val ignoredLibraryKeys = mutableSetOf<String>()
        val ignoredPluginKeys = mutableSetOf<String>()

        val versionRefsPositions = mutableMapOf<String, VersionPosition>()
        val libraryVersionPositions = mutableMapOf<String, VersionPosition>()
        val pluginVersionPositions = mutableMapOf<String, VersionPosition>()

        private var currentSection: Section? = null

        private var currentKeyLine: Int = -1

        private var currentKey: String? = null

        override fun visitStandard_table(ctx: TomlParser.Standard_tableContext) {
            super.visitStandard_table(ctx)
            val sectionName = ctx.key().keyText ?: return
            val section = Section.entries.firstOrNull { it.key == sectionName }
            currentSection = section
        }

        override fun visitKey_value(ctx: TomlParser.Key_valueContext) {
            val key = ctx.key().keyText ?: return
            currentKey = key
            currentKeyLine = ctx.key().position?.start?.line ?: -1

            val simpleValue = ctx.value().string()
            val richValue = ctx.value().inline_table()
            when (currentSection) {
                Section.VERSIONS -> simpleValue
                    ?.toVersionPosition()
                    ?.let { versionRefsPositions[key] = it }

                Section.LIBRARIES -> if (simpleValue != null) {
                    simpleValue
                        .toVersionPosition()
                        ?.let { libraryVersionPositions[key] = it }
                } else if (richValue != null) {
                    visitChildren(richValue)
                }

                Section.PLUGINS -> if (simpleValue != null) {
                    simpleValue
                        .toVersionPosition()
                        ?.let { pluginVersionPositions[key] = it }
                } else if (richValue != null) {
                    visitChildren(richValue)
                }

                else -> Unit
            }
        }

        override fun visitInline_table_keyvals(ctx: TomlParser.Inline_table_keyvalsContext) {
            var current = ctx.inline_table_keyvals_non_empty()
            var valueContext: TomlParser.ValueContext? = null
            while (current != null) {
                if (current.key().keyText == "version") {
                    valueContext = current.value()
                    break
                }
                current = current.inline_table_keyvals_non_empty()
            }
            if (valueContext == null) return
            val key = currentKey ?: return
            val versionPosition = valueContext
                .string()
                ?.toVersionPosition()
                ?: return
            when (currentSection) {
                Section.LIBRARIES -> libraryVersionPositions
                Section.PLUGINS -> pluginVersionPositions
                else -> null
            }?.put(key, versionPosition)
        }

        override fun visitComment(ctx: TomlParser.CommentContext) {
            super.visitComment(ctx)
            val commentText = ctx.text
            if (commentText.startsWith(IGNORE_COMMENT)) {
                val commentLine = ctx.position?.start?.line
                if (currentKey != null && currentKeyLine == commentLine) {
                    when (currentSection) {
                        Section.VERSIONS -> ignoredRefs.add(currentKey!!)
                        Section.LIBRARIES -> ignoredLibraryKeys.add(currentKey!!)
                        Section.PLUGINS -> ignoredPluginKeys.add(currentKey!!)
                        else -> Unit
                    }
                }
            }
        }

        override fun defaultResult() {
            // Nothing to do
        }
    }

    private enum class Section(val key: String) {
        VERSIONS("versions"),
        LIBRARIES("libraries"),
        BUNDLES("bundles"),
        PLUGINS("plugins"),
    }

    companion object {
        private const val IGNORE_COMMENT = "#ignoreUpdates"

        private val TomlParser.Simple_keyContext.keyText: String?
            get() = unquoted_key()?.text
                ?: quoted_key()?.text?.let { it.substring(1, it.lastIndex) }

        private val TomlParser.KeyContext.keyText: String?
            get() = simple_key()?.keyText
                ?: dotted_key()?.simple_key()?.joinToString(".") { it.keyText.orEmpty() }

        private fun TomlParser.StringContext.toVersionPosition(): VersionPosition? {
            return VersionPosition(
                position = position ?: return null,
                valueText = text
            )
        }
    }
}