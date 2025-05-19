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

import kotlin.test.Test
import kotlin.test.assertEquals

class ExclusionsTest {

    private fun testMatches(exclusion: Exclusion<*>, value: String, matches: Boolean) {
        when (exclusion) {
            is LibraryExclusion -> assertEquals(
                expected = matches,
                actual = exclusion.isExcluded(Dependency.Library(module = value)),
                message = if (exclusion.name != null) {
                    buildString {
                        append(exclusion.group)
                        append(':')
                        append(exclusion.name)
                        append(" should ")
                        if (!matches) append("not ")
                        append("match ")
                        append(value)
                        append(" but did")
                        if (matches) append(" not")
                    }
                } else {
                    buildString {
                        append(exclusion.group)
                        append(" should ")
                        if (!matches) append("not ")
                        append("match ")
                        append(value)
                        append(" but did")
                        if (matches) append(" not")
                    }
                }
            )

            is PluginExclusion -> assertEquals(
                expected = matches,
                actual = exclusion.isExcluded(Dependency.Plugin(value))
            )
        }
    }

    private fun assertMatching(exclusion: Exclusion<*>, value: String) {
        testMatches(exclusion, value, true)
    }

    private fun assertNotMatching(exclusion: Exclusion<*>, value: String) {
        testMatches(exclusion, value, false)
    }

    @Test
    fun testLibraryExclusions() {
        assertMatching(LibraryExclusion("com.example", "name"), "com.example:name")
        assertNotMatching(LibraryExclusion("com.example", "name"), "com.example:otherName")
        assertNotMatching(LibraryExclusion("com.example", "name"), "com.other:name")
    }

    @Test
    fun testPluginExclusion() {
        assertMatching(PluginExclusion("com.example.plugin"), "com.example.plugin")
        assertNotMatching(PluginExclusion("com.example.plugin"), "com.other.plugin")
        assertNotMatching(PluginExclusion("com.example.plugin"), "com.example.otherPlugin")
    }
}