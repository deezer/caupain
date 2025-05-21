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

import com.deezer.caupain.Serializable
import com.deezer.caupain.internal.packageGlobToRegularExpression

internal data class PackageSpec(
    val group: String,
    val name: String? = null,
) : Serializable {
    private val isGlob = '*' in group
    private val isUniversalGlob = isGlob && group == "**"
    private val groupGlobRegex by lazy { packageGlobToRegularExpression(group) }

    fun matches(dependency: Dependency): Boolean {
        val dependencyGroup = dependency.group
        return when {
            dependencyGroup == null -> false

            name == null -> when {
                !isGlob -> dependencyGroup == group
                isUniversalGlob -> true
                else -> groupGlobRegex.matches(dependencyGroup)
            }

            else -> dependencyGroup == group && dependency.name == name
        }
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}