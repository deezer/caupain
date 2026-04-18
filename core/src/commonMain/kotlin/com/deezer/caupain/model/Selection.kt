/*
 * MIT License
 *
 * Copyright (c) 2026 Deezer
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
 */

package com.deezer.caupain.model

import com.deezer.caupain.Serializable
import dev.drewhamilton.poko.Poko

/**
 * Configuration for excluded items
 */
public sealed interface Exclusion<D : Dependency> {

    /**
     * Checks if a dependency is excluded
     */
    public fun isExcluded(dependency: D): Boolean
}

/**
 * Library exclusion info. If name is null, group is used as a glob, with the following rules:
 * - `?`: Wildcard that matches exactly one character, other than `.`
 * - `*`: wildcard that matches zero, one or multiple characters, other than `.`
 * - `**`: Wildcard that matches zero, one or multiple packages. For example, `**.sub.name` matches
 * `com.example.sub.name`, `com.example.sub.sub.name`. `**` must be either preceded by `.` or be at
 * the beginning of the glob. `**` must be either followed by `.` or be at the end of the glob.
 * If the glob only consist of a `**`, it will be a match for everything.
 *
 * @property group The group of the library to exclude. If `name` is null, then this is interpreted as a glob
 * @property name The name of the library to exclude. If null, all libraries in the group are excluded.
 */
@Poko
public class LibraryExclusion(
    public val group: String,
    public val name: String? = null,
) : Exclusion<Dependency.Library>, Serializable {

    private val spec = PackageSpec(group, name)

    override fun isExcluded(dependency: Dependency.Library): Boolean = spec.matches(dependency)

    private companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Wrapper for plugin id exclusion.
 */
@Poko
public class PluginExclusion(public val id: String) : Exclusion<Dependency.Plugin> {

    override fun isExcluded(dependency: Dependency.Plugin): Boolean = dependency.id == id
}

/**
 * Configuration for included items
 */
public sealed interface Inclusion<D : Dependency> {

    /**
     * Checks if a dependency is included
     */
    public fun isIncluded(dependency: D): Boolean
}

/**
 * Library inclusion info. If name is null, group is used as a glob, with the following rules:
 * - `?`: Wildcard that matches exactly one character, other than `.`
 * - `*`: wildcard that matches zero, one or multiple characters, other than `.`
 * - `**`: Wildcard that matches zero, one or multiple packages. For example, `**.sub.name` matches
 * `com.example.sub.name`, `com.example.sub.sub.name`. `**` must be either preceded by `.` or be at
 * the beginning of the glob. `**` must be either followed by `.` or be at the end of the glob.
 * If the glob only consist of a `**`, it will be a match for everything.
 *
 * @property group The group of the library to include. If `name` is null, then this is interpreted as a glob
 * @property name The name of the library to include. If null, all libraries in the group are included.
 */
@Poko
public class LibraryInclusion(
    public val group: String,
    public val name: String? = null,
) : Inclusion<Dependency.Library>, Serializable {

    private val spec = PackageSpec(group, name)

    override fun isIncluded(dependency: Dependency.Library): Boolean = spec.matches(dependency)

    private companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Wrapper for plugin id inclusion.
 */
@Poko
public class PluginInclusion(public val id: String) : Inclusion<Dependency.Plugin> {

    override fun isIncluded(dependency: Dependency.Plugin): Boolean = dependency.id == id
}

internal fun Configuration.isIncluded(dependencyKey: String, dependency: Dependency): Boolean {
    if (
        includedKeys.isNotEmpty() && dependencyKey !in includedKeys
        || dependencyKey in excludedKeys
    ) {
        return false
    }
    return when (dependency) {
        is Dependency.Library -> includedLibraries.isNotEmpty()
                && includedLibraries.any { it.isIncluded(dependency) }
                || excludedLibraries.none { it.isExcluded(dependency) }
        is Dependency.Plugin -> includedPlugins.isNotEmpty()
                && includedPlugins.any { it.isIncluded(dependency) }
                || excludedPlugins.none { it.isExcluded(dependency) }
    }
}

/**
 * Filter for acceptable updates.
 */
public sealed interface Filter : Serializable {

    public val versionFilter: GradleDependencyVersion

    /**
     * Checks if a dependency matches this filter.
     */
    public fun matches(dependency: Dependency): Boolean

    /**
     * Filter for plugin updates.
     */
    @Poko
    public class PluginFilter(
        public val id: String,
        override val versionFilter: GradleDependencyVersion,
    ) : Filter {
        override fun matches(dependency: Dependency): Boolean {
            return dependency is Dependency.Plugin && dependency.id == id
        }

        private companion object {
            private const val serialVersionUID = 1L
        }
    }

    /**
     * Filter for library updates.
     * If name is null, group is used as a glob, with the following rules:
     *  * - `?`: Wildcard that matches exactly one character, other than `.`
     *  * - `*`: wildcard that matches zero, one or multiple characters, other than `.`
     *  * - `**`: Wildcard that matches zero, one or multiple packages. For example, `**.sub.name`
     *  * matches `com.example.sub.name`, `com.example.sub.sub.name`. `**` must be either preceded by
     *  * `.` or be at the beginning of the glob. `**` must be either followed by `.` or be at the
     *  * end of the glob.
     *  * If the glob only consist of a `**`, it will be a match for everything.
     *  *
     *  * @property group The group of the library to match. If `name` is null, then this is
     *  interpreted as a glob
     *  * @property name The name of the library to filter. If null, the filter applies to all
     *  libraries in the matching group.
     *  * @property versionFilter The filter for the version.
     */
    @Poko
    public class LibraryFilter(
        public val group: String,
        public val name: String? = null,
        override val versionFilter: GradleDependencyVersion,
    ) : Filter {
        private val spec = PackageSpec(group, name)

        override fun matches(dependency: Dependency): Boolean {
            return dependency is Dependency.Library && spec.matches(dependency)
        }

        private companion object {
            private const val serialVersionUID = 1L
        }
    }
}
