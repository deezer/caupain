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

package com.deezer.caupain.toml

import com.deezer.caupain.antlr.TomlLexer
import com.deezer.caupain.antlr.TomlParser
import com.deezer.caupain.antlr.TomlParserBaseVisitor
import com.deezer.caupain.model.Ignores
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import org.antlr.v4.kotlinruntime.CharStreams
import org.antlr.v4.kotlinruntime.CommonTokenStream

internal class IgnoreParser(
    private val fileSystem: FileSystem,
    private val path: Path,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun computeIgnores(): Ignores {
        val charStream = withContext(ioDispatcher) {
            fileSystem.read(path) {
                CharStreams.fromString(readUtf8())
            }
        }
        val lexer = TomlLexer(charStream)
        val tokenStream = CommonTokenStream(lexer)
        val parser = TomlParser(tokenStream)
        val visitor = IgnoreVisitor()
        visitor.visit(parser.document())
        return Ignores(
            refs = visitor.ignoredRefs,
            libraryKeys = visitor.ignoredLibraryKeys,
            pluginKeys = visitor.ignoredPluginKeys
        )
    }

    private class IgnoreVisitor : TomlParserBaseVisitor<Unit>() {

        val ignoredRefs = mutableSetOf<String>()
        val ignoredLibraryKeys = mutableSetOf<String>()
        val ignoredPluginKeys = mutableSetOf<String>()

        private var currentSection: Section? = null

        private var currentKeyLine: Int = -1

        private var currentKey: String? = null

        override fun visitStandard_table(ctx: TomlParser.Standard_tableContext) {
            super.visitStandard_table(ctx)
            val sectionName = ctx.key().text
            val section = Section.entries.firstOrNull { it.key == sectionName }
            currentSection = section
        }

        override fun visitKey_value(ctx: TomlParser.Key_valueContext) {
            super.visitKey_value(ctx)
            currentKey = ctx.key().text
            currentKeyLine = ctx.key().position?.start?.line ?: -1
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
    }
}