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

package com.deezer.caupain.plugin

import com.deezer.caupain.plugin.internal.asOptional
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register
import kotlin.jvm.optionals.getOrDefault
import kotlin.jvm.optionals.getOrNull

/**
 * Plugin to check for dependency updates.
 */
@Suppress("UnstableApiUsage")
open class DependencyUpdatePlugin : Plugin<Project> {

    override fun apply(target: Project) {
        val ext = target.extensions.create<DependenciesUpdateExtension>("caupain")
        require(target == target.rootProject) {
            "Plugin must be applied to the root project"
        }
        target.tasks.register<DependenciesUpdateTask>("checkDependencyUpdates") {
            group = "verification"
            description = "Check for dependency updates"
            versionCatalogFiles.convention(ext.resolveVersionCatalogFiles(target))
            excludedKeys.convention(ext.excludedKeys)
            excludedLibraries.convention(ext.excludedLibraries)
            excludedPluginIds.convention(ext.excludedPluginIds)
            formatterOutputs.convention(ext.outputsHandler.outputs)
            repositories.convention(ext.repositories.libraries)
            pluginRepositories.convention(ext.repositories.plugins)
            useCache.convention(ext.useCache)
            onlyCheckStaticVersions.convention(ext.onlyCheckStaticVersions)
        }
    }

    private fun DependenciesUpdateExtension.resolveVersionCatalogFiles(project: Project): Provider<Iterable<FileSystemLocation>> {
        val defaultVersionCatalogFile = project
            .layout
            .projectDirectory
            .file("gradle/libs.versions.toml")
        return versionCatalogFiles
            .elements
            .asOptional()
            .flatMap { files ->
                val isDefaultValueForCollection = files.getOrDefault(emptySet()).isEmpty()
                versionCatalogFile
                    .asOptional()
                    .map { file ->
                        val isDefaultValueForFile = !file.isPresent
                        when {
                            !isDefaultValueForFile && !isDefaultValueForCollection -> {
                                project.logger.warn("Both versionCatalogFile and versionCatalogFiles are set. versionCatalogFile will be ignored.")
                                files.get()
                            }

                            !isDefaultValueForFile -> listOf(file.get())

                            else -> files
                                .getOrNull()
                                ?.takeUnless { it.isEmpty() }
                                ?: setOf(defaultVersionCatalogFile)
                        }
                    }
            }
    }
}