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

package com.deezer.caupain.formatting

import com.deezer.caupain.formatting.markdown.MarkdownFormatter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.intellij.lang.annotations.Language

@OptIn(ExperimentalCoroutinesApi::class)
class MarkdownFormatterTest : FileFormatterTest() {

    override val extension: String = "md"

    override val emptyResult: String
        get() = EMPTY_RESULT

    override val fullResult: String
        get() = FULL_RESULT

    override fun createFormatter(ioDispatcher: CoroutineDispatcher) =
        MarkdownFormatter(ioDispatcher)
}

@Language("Markdown")
private const val EMPTY_RESULT = "# No updates available."

@Language("Markdown")
private val FULL_RESULT = """
# Dependency updates
## Caupain
Caupain current version is 1.0.0 whereas last version is 1.1.0
You can update Caupain via :
- plugins
- [Github releases](https://github.com/deezer/caupain/releases)
- Hombrew
- apt
## Gradle
Gradle current version is 1.0 whereas last version is 1.1. See [https://docs.gradle.org/1.1/release-notes.html](https://docs.gradle.org/1.1/release-notes.html).
## Version References
| Id     | Current version | Updated version | Details                                                                                                                                                                               |
| ------ | --------------- | --------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| deezer | 1.0.0           | 2.0.0           | Libraries: library<br/>Plugins: plugin<br/>Updates for these dependency using the reference were not found for the updated version:<ul><li>other-library: (no update found)</li></ul> |
## Libraries
| Id                 | Name | Current version | Updated version | URL                                                                                                    |
| ------------------ | ---- | --------------- | --------------- | ------------------------------------------------------------------------------------------------------ |
| com.deezer:library |      | 1.0.0           | 2.0.0           | [Release notes](http://www.example.com/library/releases)<br/>[Project](http://www.example.com/library) |
## Plugins
| Id                | Name | Current version | Updated version | URL                                                                                                  |
| ----------------- | ---- | --------------- | --------------- | ---------------------------------------------------------------------------------------------------- |
| com.deezer:plugin |      | 1.0.0           | 2.0.0           | [Release notes](http://www.example.com/plugin/releases)<br/>[Project](http://www.example.com/plugin) |
## Ignored
| Id                         | Current version | Updated version |
| -------------------------- | --------------- | --------------- |
| com.deezer:ignored-library | 1.0.0           | 2.0.0           |
""".trimIndent()