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
 *
 * This file incorporates work covered by the following copyright and permission notice:
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.deezer.caupain.internal

// Adapted from Apache Freemarker
@Suppress("NestedBlockDepth") // Taken from Java class
internal fun packageGlobToRegularExpression(glob: String): Regex {
    val regex = StringBuilder()
    var nextStart = 0
    val ln = glob.length
    var idx = 0
    while (idx < ln) {
        when (glob[idx]) {
            '?' -> {
                regex.appendLiteralGlobSection(glob, nextStart, idx)
                regex.append("[^\\.]")
                nextStart = idx + 1
            }

            '*' -> {
                regex.appendLiteralGlobSection(glob, nextStart, idx)
                if (idx + 1 < ln && glob[idx + 1] == '*') {
                    require(idx == 0 || glob[idx - 1] == '.') {
                        "The \"**\" wildcard must be directly after a \".\" or it must be at the beginning"
                    }
                    if (idx + 2 == ln) {
                        // Trailing "**"
                        regex.append(".*")
                        idx++ // Discard next '*'
                    } else {
                        // "**."
                        require(idx + 2 < ln && glob[idx + 2] == '.') {
                            "The \"**\" wildcard must be followed by a \".\" or it must be at the end"
                        }
                        regex.append("(.*?\\.)*")
                        idx += 2 // Discard next '*' and dot
                    }
                } else {
                    regex.append("[^\\.]*")
                }
                nextStart = idx + 1
            }
        }
        idx++
    }
    regex.appendLiteralGlobSection(glob, nextStart, ln)
    return Regex(regex.toString())
}

private fun StringBuilder.appendLiteralGlobSection(glob: String, start: Int, end: Int) {
    if (start != end) append(Regex.escape(glob.substring(start, end)))
}