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

package com.deezer.caupain.cli.model

import com.deezer.caupain.cli.serialization.ConfigurationSerializer
import com.deezer.caupain.model.LibraryExclusion
import com.deezer.caupain.model.Logger
import com.deezer.caupain.model.PluginExclusion
import com.deezer.caupain.model.gradle.GradleStabilityLevel
import kotlinx.serialization.Serializable
import okio.Path
import com.deezer.caupain.model.Configuration as ModelConfiguration

@Serializable(ConfigurationSerializer::class)
interface Configuration {
    val repositories: List<Repository>?
    val pluginRepositories: List<Repository>?
    val versionCatalogPath: Path?
    val versionCatalogPaths: Iterable<Path>?
    val excludedKeys: Set<String>?
    val excludedLibraries: List<LibraryExclusion>?
    val excludedPlugins: List<PluginExclusion>?
    val policy: String?
    val policyPluginDir: Path?
    val cacheDir: Path?
    val showVersionReferences: Boolean?
    val outputType: OutputType?
    val outputPath: Path?
    val gradleWrapperPropertiesPath: Path?
    val onlyCheckStaticVersions: Boolean?
    val gradleStabilityLevel: GradleStabilityLevel?
    val checkIgnored: Boolean?
    val replace: Boolean?

    fun validate(logger: Logger)

    fun toConfiguration(baseConfiguration: ModelConfiguration): ModelConfiguration

    enum class OutputType {
        CONSOLE, HTML, MARKDOWN, JSON
    }
}