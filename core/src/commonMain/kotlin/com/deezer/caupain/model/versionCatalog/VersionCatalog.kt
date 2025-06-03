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

package com.deezer.caupain.model.versionCatalog

import com.deezer.caupain.model.Dependency
import kotlinx.serialization.Serializable

/**
 * Gradle version catalog.
 */
@Serializable
public class VersionCatalog(
    public val versions: Map<String, Version.Resolved> = emptyMap(),
    public val libraries: Map<String, Dependency.Library> = emptyMap(),
    public val plugins: Map<String, Dependency.Plugin> = emptyMap()
) {
    internal val dependencies: Map<String, Dependency>
        get() = libraries + plugins

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as VersionCatalog

        if (versions != other.versions) return false
        if (libraries != other.libraries) return false
        if (plugins != other.plugins) return false

        return true
    }

    override fun hashCode(): Int {
        var result = versions.hashCode()
        result = 31 * result + libraries.hashCode()
        result = 31 * result + plugins.hashCode()
        return result
    }

    override fun toString(): String {
        return "VersionCatalog(versions=$versions, libraries=$libraries, plugins=$plugins)"
    }
}
