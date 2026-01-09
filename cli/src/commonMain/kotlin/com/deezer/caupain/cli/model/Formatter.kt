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