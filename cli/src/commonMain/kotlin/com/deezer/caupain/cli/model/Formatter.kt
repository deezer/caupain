/*
 * MIT License
 *
 * Copyright (c) 2026 Deezer
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
 *
 */

package com.deezer.caupain.cli.model

import com.deezer.caupain.formatting.SinkFormatter
import com.deezer.caupain.formatting.console.ConsoleFormatter
import com.deezer.caupain.formatting.html.HtmlFormatter
import com.deezer.caupain.formatting.json.JsonFormatter
import com.deezer.caupain.formatting.markdown.MarkdownFormatter
import com.deezer.caupain.formatting.model.Input

sealed interface Formatter {

    val outputType: Configuration.OutputType

    class Console(private val formatter: ConsoleFormatter) : Formatter {
        override val outputType = Configuration.OutputType.CONSOLE

        fun format(input: Input) {
            formatter.format(input)
        }
    }

    sealed class Sink(
        private val formatter: SinkFormatter,
        override val outputType: Configuration.OutputType
    ) : Formatter {
        suspend fun format(input: Input, sink: okio.Sink) {
            formatter.format(input, sink)
        }
    }

    class Html(formatter: HtmlFormatter) : Sink(formatter, Configuration.OutputType.HTML)

    class Markdown(formatter: MarkdownFormatter) :
        Sink(formatter, Configuration.OutputType.MARKDOWN)

    class Json(formatter: JsonFormatter) : Sink(formatter, Configuration.OutputType.JSON)
}
