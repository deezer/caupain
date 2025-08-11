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

package com.deezer.caupain.model

import dev.drewhamilton.poko.Poko
import kotlinx.serialization.Serializable
import org.antlr.v4.kotlinruntime.ast.Point as ANTLRPoint

/**
 * Represents a point in a text document, defined by its line and column.
 *
 * @property line The line number (0-based).
 * @property column The column number (0-based).
 */
@Serializable
@Poko
public class Point(
    public val line: Int,
    public val column: Int
) : Comparable<Point> {
    internal constructor(point: ANTLRPoint) : this(
        line = point.line - 1,
        column = point.column
    )

    override fun compareTo(other: Point): Int =
        compareValuesBy(this, other, Point::line, Point::column)
}