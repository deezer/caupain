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

import com.deezer.caupain.model.Repository
import com.deezer.caupain.plugin.internal.asOptional
import com.deezer.caupain.plugin.internal.create
import com.deezer.caupain.plugin.internal.register
import com.deezer.caupain.plugin.internal.setProperty
import com.deezer.caupain.plugin.internal.toRepositories
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile
import org.gradle.api.internal.GradleInternal
import org.gradle.api.provider.Provider
import org.gradle.util.GradleVersion
import kotlin.jvm.optionals.getOrNull

/**
 * Plugin to check for dependency updates.
 */
@Suppress("UnstableApiUsage")
open class DependencyUpdatePlugin : Plugin<Project> {

    override fun apply(target: Project) {
        require(target == target.rootProject) {
            "Plugin must be applied to the root project"
        }
        require(GradleVersion.current() >= GradleVersion.version("9.0")) {
            "Caupain plugin requires Gradle 9.0 or higher"
        }
        val ext = target.extensions.create<DependenciesUpdateExtension>("caupain")
        ext.initDefaultRepositories(target)
        val versionCatalogFilesProvider = ext.versionCatalogFilesProvider(target)

        val checkTaskProvider = target.tasks.register<DependenciesUpdateTask>("checkDependencyUpdates") {
            group = "verification"
            description = "Check for dependency updates"
            versionCatalogFiles.convention(versionCatalogFilesProvider)
            excludedKeys.convention(ext.excludedKeys)
            excludedLibraries.convention(ext.excludedLibraries)
            excludedPluginIds.convention(ext.excludedPluginIds)
            formatterOutputs.convention(ext.outputsHandler.outputs)
            showVersionReferences.convention(ext.showVersionReferences)
            repositories.convention(ext.repositories.libraries)
            pluginRepositories.convention(ext.repositories.plugins)
            useCache.convention(ext.useCache)
            onlyCheckStaticVersions.convention(ext.onlyCheckStaticVersions)
            gradleStabilityLevel.convention(ext.gradleStabilityLevel)
            checkIgnored.convention(ext.checkIgnored)
        }
        target.tasks.register<DependenciesReplaceTask>("replaceOutdatedDependencies") {
            group = "verification"
            description = "Check for dependency updates"
            versionCatalogFile.convention(
                versionCatalogFilesProvider.map { files ->
                    files.firstNotNullOf { it as? RegularFile }
                }
            )
            serializedUpdates.fileProvider(
                checkTaskProvider.map { checkTask ->
                    checkTask
                        .outputs
                        .files
                        .filter { it.name == DependenciesUpdateTask.SERIALIZED_UPDATES_FILE_NAME }
                        .singleFile
                }
            )
        }
    }

    private fun DependenciesUpdateExtension.initDefaultRepositories(target: Project) {
        val collectedRepositories = target.objects.setProperty<Repository>()
        val collectedPluginRepositories = target.objects.setProperty<Repository>()

        // First, collect repositories from settings
        val settings = (target.gradle as GradleInternal).settings
        collectedRepositories.addAll(
            settings.dependencyResolutionManagement.repositories.toRepositories(target.objects)
        )
        collectedPluginRepositories.addAll(
            settings.pluginManagement.repositories.toRepositories(target.objects)
        )
        // Then go into projects to gather specific repositories
        target.allprojects { project ->
            project.afterEvaluate { evaluatedProject ->
                collectedPluginRepositories.addAll(
                    evaluatedProject
                        .buildscript
                        .repositories
                        .toRepositories(target.objects)
                )
                collectedRepositories.addAll(
                    evaluatedProject
                        .repositories
                        .toRepositories(target.objects)
                )
            }
        }

        repositories.setupConvention(collectedRepositories, collectedPluginRepositories)
    }

    private fun DependenciesUpdateExtension.versionCatalogFilesProvider(project: Project): Provider<Iterable<FileSystemLocation>> {
        val defaultVersionCatalogFile = project
            .layout
            .projectDirectory
            .file("gradle/libs.versions.toml")
        return versionCatalogFiles
            .elements
            .asOptional()
            .flatMap { files ->
                val isDefaultValueForCollection = files.getOrNull().isNullOrEmpty()
                versionCatalogFile
                    .asOptional()
                    .map { file ->
                        val isDefaultValueForFile = !file.isPresent
                        when {
                            !isDefaultValueForFile && !isDefaultValueForCollection -> {
                                project.logger.warn("Both versionCatalogFile and versionCatalogFiles are set. versionCatalogFile will be ignored.")
                                files.get()
                            }

                            !isDefaultValueForFile -> setOf(file.get())

                            else -> files
                                .getOrNull()
                                ?.takeUnless { it.isEmpty() }
                                ?: setOf(defaultVersionCatalogFile)
                        }
                    }
            }
    }
}