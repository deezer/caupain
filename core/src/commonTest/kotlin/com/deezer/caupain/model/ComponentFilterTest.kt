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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComponentFilterTest {

    private fun assertAccepts(filter: ComponentFilter, definition: String) {
        assertTrue(filter.accepts(Dependency.Library(module = definition)))
    }

    private fun assertDoesNotAccept(filter: ComponentFilter, definition: String) {
        assertFalse(filter.accepts(Dependency.Library(module = definition)))
    }

    @Test
    fun testExcludes() {
        val filter = DefaultComponentFilter(
            includes = listOf(),
            excludes = listOf(
                PackageSpec(group = "com.example.**"),
                PackageSpec(group = "com.example2.**"),
            )
        )
        assertAccepts(filter, "com.other.test")
        assertDoesNotAccept(filter, "com.example.test")
        assertDoesNotAccept(filter, "com.example2.test")
    }

    @Test
    fun testIncludes() {
        val filter = DefaultComponentFilter(
            includes = listOf(
                PackageSpec(group = "com.example.**"),
                PackageSpec(group = "com.example2.**"),
            ),
            excludes = listOf()
        )
        assertDoesNotAccept(filter, "com.other.test")
        assertAccepts(filter, "com.example.test")
        assertAccepts(filter, "com.example2.test")
    }

    @Test
    fun testIncludesExcludes() {
        val filter = DefaultComponentFilter(
            includes = listOf(
                PackageSpec(group = "com.example.**"),
                PackageSpec(group = "com.example2.**"),
            ),
            excludes = listOf(
                PackageSpec(group = "com.example.specific")
            )
        )
        assertDoesNotAccept(filter, "com.other.test")
        assertAccepts(filter, "com.example.test")
        assertAccepts(filter, "com.example2.test")
        assertDoesNotAccept(filter, "com.example.specific")
    }

    @Test
    fun testEmpty() {
        val filter = DefaultComponentFilter(includes = listOf(), excludes = listOf())
        assertAccepts(filter, "com.other.test")
        assertAccepts(filter, "com.example.test")
        assertAccepts(filter, "com.example2.test")
    }
}