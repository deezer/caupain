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
import com.deezer.caupain.plugin.internal.create
import com.deezer.caupain.plugin.internal.register
import com.deezer.caupain.plugin.internal.setProperty
import com.deezer.caupain.plugin.internal.toRepositories
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.configuration.BuildFeatures
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.internal.ConfigurationCacheDegradation
import org.gradle.api.internal.GradleInternal
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.wrapper.Wrapper
import org.gradle.util.GradleVersion
import java.util.Properties
import javax.inject.Inject
import kotlin.jvm.optionals.getOrNull

/**
 * Plugin to check for dependency updates.
 */
@Suppress("UnstableApiUsage")
open class DependencyUpdatePlugin @Inject constructor(
    private val buildFeatures: BuildFeatures,
    private val objects: ObjectFactory,
    private val providers: ProviderFactory,
) : Plugin<Project> {

    override fun apply(target: Project) {
        require(target == target.rootProject) {
            "Plugin must be applied to the root project"
        }
        require(GradleVersion.current() >= GradleVersion.version("9.0")) {
            "Caupain plugin requires Gradle 9.0 or higher"
        }
        val ext = target.extensions.create<DependenciesUpdateExtension>("caupain")
        if (!buildFeatures.isolatedProjects.active.get()) {
            ext.initDefaultRepositories(target)
        }
        val versionCatalogFilesProvider = ext.versionCatalogFilesProvider(target)

        val checkTaskProvider = target.tasks.registerCheckTask(
            ext = ext,
            versionCatalogFilesProvider = versionCatalogFilesProvider,
        )
        val wrapperUpdateTask = target.tasks.register<Wrapper>("updateWrapper") {
            notCompatibleWithConfigurationCache(
                "Use specific condition to launch and changes parameter in startup"
            )
            ConfigurationCacheDegradation.requireDegradation(
                this,
                "Use specific condition to launch and changes parameter in startup"
            )
        }
        val replaceTask = target.tasks.registerReplaceTask(
            versionCatalogFilesProvider = versionCatalogFilesProvider,
            checkTaskProvider = checkTaskProvider,
            wrapperUpdateTaskProvider = wrapperUpdateTask,
            layout = target.layout,
        )
        val replaceTaskOutputProperties = replaceTask
            .flatMap { it.wrapperArguments }
            .filter { it.asFile.exists() }
            .map { file ->
                file.asFile.bufferedReader().use { reader ->
                    Properties().apply { load(reader) }
                }
            }
        wrapperUpdateTask.configure { task ->
            task.onlyIf {
                replaceTaskOutputProperties
                    .orNull
                    ?.getProperty(DependenciesReplaceTask.CAN_UPDATE_WRAPPER)
                    ?.toBoolean() == true
            }
            task.doFirst {
                val properties = replaceTaskOutputProperties.get()
                task.gradleVersion =
                    properties.getProperty(DependenciesReplaceTask.GRADLE_UPDATE_VERSION)
                task.distributionSha256Sum =
                    properties.getProperty(DependenciesReplaceTask.GRADLE_UPDATE_CHECKSUM)
            }
            task.dependsOn(replaceTask)
        }
    }

    private fun TaskContainer.registerCheckTask(
        ext: DependenciesUpdateExtension,
        versionCatalogFilesProvider: Provider<Iterable<FileSystemLocation>>,
    ): TaskProvider<DependenciesUpdateTask> {
        return register<DependenciesUpdateTask>("checkDependencyUpdates") {
            group = "verification"
            description = "Check for dependency updates"
            notCompatibleWithConfigurationCache("Uses Gradle internal API for repositories")
            versionCatalogFiles.convention(versionCatalogFilesProvider)
            excludedKeys.convention(ext.excludedKeys)
            excludedLibraries.convention(ext.excludedLibraries)
            excludedPluginIds.convention(ext.excludedPluginIds)
            includedKeys.convention(ext.includedKeys)
            includedLibraries.convention(ext.includedLibraries)
            includedPluginIds.convention(ext.includedPluginIds)
            filters.convention(ext.filters)
            formatterOutputs.convention(ext.outputsHandler.outputs)
            showVersionReferences.convention(ext.showVersionReferences)
            repositories.convention(ext.repositories.libraries)
            pluginRepositories.convention(ext.repositories.plugins)
            useCache.convention(ext.useCache)
            onlyCheckStaticVersions.convention(ext.onlyCheckStaticVersions)
            gradleStabilityLevel.convention(ext.gradleStabilityLevel)
            checkIgnored.convention(ext.checkIgnored)
            searchReleaseNote.convention(ext.searchReleaseNote)
            githubToken.convention(ext.githubToken)
            verifyExistence.convention(ext.verifyExistence)
            doNotCheckSelfUpdates.convention(ext.doNotCheckSelfUpdates)
            if (buildFeatures.configurationCache.active.get()) {
                ConfigurationCacheDegradation.requireDegradation(
                    this,
                    ext.repositories.isCompatibleWithConfigCache.map { isCompatible ->
                        if (isCompatible) {
                            null
                        } else {
                            "Configured repositories are not compatible with configuration cache"
                        }
                    }
                )
            }
        }
    }

    private fun TaskContainer.registerReplaceTask(
        versionCatalogFilesProvider: Provider<Iterable<FileSystemLocation>>,
        checkTaskProvider: TaskProvider<DependenciesUpdateTask>,
        wrapperUpdateTaskProvider: TaskProvider<Wrapper>,
        layout: ProjectLayout,
    ): TaskProvider<DependenciesReplaceTask> {
        return register<DependenciesReplaceTask>("replaceOutdatedDependencies") {
            group = "verification"
            description = "Replace dependency updates in-line with their latest versions"
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
            wrapperArguments.set(
                layout.buildDirectory.file("caupain/wrapper-update.properties")
            )
            finalizedBy(wrapperUpdateTaskProvider)
        }
    }

    private fun DependenciesUpdateExtension.initDefaultRepositories(rootProject: Project) {
        val defaultLibraryRepositories = objects.setProperty<RepositoryInfo>()
        val defaultPluginRepositories = objects.setProperty<RepositoryInfo>()

        // First, collect repositories from settings
        val settings = (rootProject.gradle as GradleInternal).settings
        defaultLibraryRepositories.addAll(
            settings
                .dependencyResolutionManagement
                .repositories
                .toRepositories(
                    objects = objects,
                    providers = providers,
                )
        )
        defaultPluginRepositories.addAll(
            settings
                .pluginManagement
                .repositories
                .toRepositories(
                    objects = objects,
                    providers = providers,
                )
        )
        // Then go into projects to gather specific repositories
        rootProject.allprojects { project ->
            project.afterEvaluate { evaluatedProject ->
                defaultPluginRepositories.addAll(
                    evaluatedProject
                        .buildscript
                        .repositories
                        .toRepositories(
                            objects = objects,
                            providers = providers,
                        )
                )
                defaultLibraryRepositories.addAll(
                    evaluatedProject
                        .repositories
                        .toRepositories(
                            objects = objects,
                            providers = providers,
                        )
                )
            }
        }

        repositories.libraryRepositoryInfos.convention(defaultLibraryRepositories)
        repositories.pluginRepositoryInfos.convention(defaultPluginRepositories)
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
