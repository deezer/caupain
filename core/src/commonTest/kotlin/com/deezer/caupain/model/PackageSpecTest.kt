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

class PackageSpecTest {

    private fun testMatches(spec: PackageSpec, value: String, matches: Boolean) {
        assertEquals(
            expected = matches,
            actual = spec.matches(Dependency.Library(module = value)),
            message = if (spec.name != null) {
                buildString {
                    append(spec.group)
                    append(':')
                    append(spec.name)
                    append(" should ")
                    if (!matches) append("not ")
                    append("match ")
                    append(value)
                    append(" but did")
                    if (matches) append(" not")
                }
            } else {
                buildString {
                    append(spec.group)
                    append(" should ")
                    if (!matches) append("not ")
                    append("match ")
                    append(value)
                    append(" but did")
                    if (matches) append(" not")
                }
            }
        )
    }

    private fun assertMatching(spec: PackageSpec, value: String) {
        testMatches(spec, value, true)
    }

    private fun assertNotMatching(spec: PackageSpec, value: String) {
        testMatches(spec, value, false)
    }

    @Test
    fun testSimpleExclusions() {
        assertMatching(PackageSpec("com.example", "name"), "com.example:name")
        assertNotMatching(PackageSpec("com.example", "name"), "com.example:otherName")
        assertNotMatching(PackageSpec("com.example", "name"), "com.other:name")
        assertMatching(PackageSpec("com.example"), "com.example:name")
        assertMatching(PackageSpec("com.example"), "com.example:otherName")
        assertNotMatching(PackageSpec("com.example"), "com.other:name")
    }

    @Test
    fun testGlobExclusion() {
        assertMatching(PackageSpec("com.example.*"), "com.example.sub:name")
        assertNotMatching(PackageSpec("com.example.*"), "com.other.sub:name")
        assertNotMatching(PackageSpec("com.example.*"), "com.other.sub.subsub:name")
        assertNotMatching(PackageSpec("com.example.*"), "com.example:name")
        assertMatching(PackageSpec("com.example.**"), "com.example.sub:name")
        assertMatching(PackageSpec("com.example.**"), "com.example.sub.sub:name")
        assertNotMatching(PackageSpec("com.example.**"), "com.other.sub:name")
        assertMatching(PackageSpec("**"), "com.example.sub.sub:name")
        assertMatching(PackageSpec("**"), "com.other.sub.sub:name")
        assertMatching(PackageSpec("com.**.sub"), "com.example.sub:name")
        assertMatching(PackageSpec("com.**.sub"), "com.other.sub:name")
        assertNotMatching(PackageSpec("com.**.sub"), "com.example.otherSub:name")
        assertMatching(PackageSpec("com.*.sub"), "com.example.sub:name")
        assertMatching(PackageSpec("com.*.sub"), "com.other.sub:name")
        assertNotMatching(PackageSpec("com.*.sub"), "com.example.otherSub:name")
        assertNotMatching(PackageSpec("com.*.sub"), "com.example.sub.subsub:name")
        assertMatching(PackageSpec("com.*.sub.**"), "com.example.sub.subsub:name")
        assertMatching(PackageSpec("com.*.sub.**"), "com.other.sub.subsub:name")
        assertMatching(PackageSpec("com.*.sub.**"), "com.example.sub.otherSub:name")
        assertNotMatching(PackageSpec("com.*.sub.**"), "com.example.otherSub.otherSub:name")
        assertMatching(PackageSpec("com.ex?mple.*"), "com.example.sub:name")
        assertMatching(PackageSpec("com.ex?mple.*"), "com.exomple.sub:name")
        assertNotMatching(PackageSpec("com.ex?mple.*"), "com.other.sub:name")
        assertNotMatching(PackageSpec("com.ex?mple.*"), "com.other.sub.subsub:name")
        assertNotMatching(PackageSpec("com.ex?mple.*"), "com.example:name")
    }
}