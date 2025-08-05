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

import com.deezer.caupain.model.versionCatalog.Version
import kotlinx.serialization.Serializable
import org.antlr.v4.kotlinruntime.ast.Position

/**
 * Represents additional information about a version catalog.
 *
 * @property ignores The elements to ignore in the version catalog.
 * @property positions The positions of various elements in the version catalog.
 */
public class VersionCatalogInfo(
    public val ignores: Ignores = Ignores(),
    public val positions: Positions = Positions()
) {
    /**
     * Represents the elements to ignore in the version catalog.
     */
    public class Ignores(
        public val refs: Set<String> = emptySet(),
        public val libraryKeys: Set<String> = emptySet(),
        public val pluginKeys: Set<String> = emptySet()
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as Ignores

            if (refs != other.refs) return false
            if (libraryKeys != other.libraryKeys) return false
            if (pluginKeys != other.pluginKeys) return false

            return true
        }

        override fun hashCode(): Int {
            var result = refs.hashCode()
            result = 31 * result + libraryKeys.hashCode()
            result = 31 * result + pluginKeys.hashCode()
            return result
        }

        override fun toString(): String {
            return "Ignores(refs=$refs, libraryKeys=$libraryKeys, pluginKeys=$pluginKeys)"
        }
    }

    /**
     * Represents the positions of various elements in the version catalog,
     */
    @Serializable
    public class Positions(
        public val versionRefsPositions: Map<String, VersionPosition> = emptyMap(),
        public val libraryVersionPositions: Map<String, VersionPosition> = emptyMap(),
        public val pluginVersionPositions: Map<String, VersionPosition> = emptyMap()
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as Positions

            if (versionRefsPositions != other.versionRefsPositions) return false
            if (libraryVersionPositions != other.libraryVersionPositions) return false
            if (pluginVersionPositions != other.pluginVersionPositions) return false

            return true
        }

        override fun hashCode(): Int {
            var result = versionRefsPositions.hashCode()
            result = 31 * result + libraryVersionPositions.hashCode()
            result = 31 * result + pluginVersionPositions.hashCode()
            return result
        }

        override fun toString(): String {
            return "Positions(versionRefsPositions=$versionRefsPositions, libraryVersionPositions=$libraryVersionPositions, pluginVersionPositions=$pluginVersionPositions)"
        }
    }

    /**
     * Represents a position in the version catalog, including the starting point,
     * the number of lines spanned, and the text value at that position.
     */
    @Serializable
    public class VersionPosition(
        public val startPoint: Point,
        public val nbLines: Int,
        public val valueText: String
    ) {
        internal constructor(
            position: Position,
            valueText: String
        ) : this(
            startPoint = Point(position.start),
            nbLines = position.end.line - position.start.line + 1,
            valueText = valueText
        )

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as VersionPosition

            if (nbLines != other.nbLines) return false
            if (startPoint != other.startPoint) return false
            if (valueText != other.valueText) return false

            return true
        }

        override fun hashCode(): Int {
            var result = nbLines
            result = 31 * result + startPoint.hashCode()
            result = 31 * result + valueText.hashCode()
            return result
        }

        override fun toString(): String {
            return "VersionPosition(startPoint=$startPoint, nbLines=$nbLines, valueText='$valueText')"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as VersionCatalogInfo

        if (ignores != other.ignores) return false
        if (positions != other.positions) return false

        return true
    }

    override fun hashCode(): Int {
        var result = ignores.hashCode()
        result = 31 * result + positions.hashCode()
        return result
    }

    override fun toString(): String {
        return "VersionCatalogInfo(ignores=$ignores, positions=$positions)"
    }
}

internal fun VersionCatalogInfo.Ignores.isExcluded(key: String, dependency: Dependency): Boolean {
    return when {
        dependency.version is Version.Reference ->
            (dependency.version as? Version.Reference)?.ref in refs

        dependency is Dependency.Library -> key in libraryKeys

        dependency is Dependency.Plugin -> key in pluginKeys

        else -> false
    }
}