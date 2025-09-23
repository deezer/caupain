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

import com.deezer.caupain.DependencyUpdateChecker
import com.deezer.caupain.DependencyVersionsReplacer
import com.deezer.caupain.formatting.console.ConsoleFormatter
import com.deezer.caupain.formatting.console.ConsolePrinter
import com.deezer.caupain.formatting.model.Input
import com.deezer.caupain.model.Configuration
import com.deezer.caupain.model.Dependency
import com.deezer.caupain.model.GradleDependencyVersion
import com.deezer.caupain.model.LibraryExclusion
import com.deezer.caupain.model.PluginExclusion
import com.deezer.caupain.model.Repository
import com.deezer.caupain.model.StabilityLevelPolicy
import com.deezer.caupain.model.gradle.GradleConstants
import com.deezer.caupain.model.gradle.GradleStabilityLevel
import com.deezer.caupain.model.versionCatalog.Version
import com.deezer.caupain.plugin.internal.DefaultJson
import com.deezer.caupain.plugin.internal.listProperty
import com.deezer.caupain.plugin.internal.property
import com.deezer.caupain.plugin.internal.setProperty
import com.deezer.caupain.plugin.internal.toOkioPath
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.encodeToStream
import okio.Path.Companion.toOkioPath
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.logging.Logger
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction
import org.gradle.util.GradleVersion
import org.gradle.work.DisableCachingByDefault
import java.util.UUID

/**
 * Dependencies Update task.
 */
@OptIn(ExperimentalSerializationApi::class)
@DisableCachingByDefault(because = "Will never be up to date")
open class DependenciesUpdateTask : DefaultTask() {

    @get:Internal
    val repositories = project.objects.listProperty<Repository>()

    @get:Internal
    val pluginRepositories = project.objects.listProperty<Repository>()

    /**
     * @see DependenciesUpdateExtension.versionCatalogFiles
     * @see DependenciesUpdateExtension.versionCatalogFile
     */
    @get:Internal
    val versionCatalogFiles: ConfigurableFileCollection = project.objects.fileCollection()

    /**
     * @see DependenciesUpdateExtension.excludedKeys
     */
    @get:Internal
    val excludedKeys = project.objects.setProperty<String>()

    /**
     * @see DependenciesUpdateExtension.excludedLibraries
     */
    @get:Internal
    val excludedLibraries = project.objects.listProperty<LibraryExclusion>()

    /**
     * @see DependenciesUpdateExtension.excludedPluginIds
     */
    @get:Internal
    val excludedPluginIds = project.objects.setProperty<String>()

    @get:Internal
    internal val formatterOutputs = project.objects.listProperty<OutputsHandler.Output>()

    @get:Internal
    internal val replacerInputFile: Provider<RegularFile> = project
        .layout
        .buildDirectory
        .file("dependencies/$SERIALIZED_UPDATES_FILE_NAME")

    private val customFormatter = project.objects.property<Formatter>()

    @get:Internal
    internal val showVersionReferences = project.objects.property<Boolean>()

    @get:OutputFiles
    internal val outputFiles: Provider<List<Provider<RegularFile>>> =
        formatterOutputs.map { outputs ->
            buildList {
                outputs.mapNotNullTo(this) { output ->
                    (output as? OutputsHandler.Output.File)?.file
                }
                add(replacerInputFile)
            }
        }

    /**
     * @see DependenciesUpdateExtension.useCache
     */
    @get:Internal
    val useCache = project.objects.property<Boolean>()

    /**
     * @see DependenciesUpdateExtension.onlyCheckStaticVersions
     */
    @get:Internal
    val onlyCheckStaticVersions = project.objects.property<Boolean>()

    /**
     * @see DependenciesUpdateExtension.gradleStabilityLevel
     */
    @get:Internal
    val gradleStabilityLevel = project.objects.property<GradleStabilityLevel>()

    /**
     * @see DependenciesUpdateExtension.checkIgnored
     */
    @get:Internal
    val checkIgnored = project.objects.property<Boolean>()

    /**
     * @see DependenciesUpdateExtension.searchReleaseNote
     */
    @get:Internal
    val searchReleaseNote = project.objects.property<Boolean>()

    /**
     * @see DependenciesUpdateExtension.githubToken
     */
    @get:Internal
    val githubToken = project.objects.property<String>()

    /**
     * The cache directory for the HTTP cache. Default is "build/cache/dependency-updates".
     */
    @get:Internal
    val cacheDir: DirectoryProperty = project
        .objects
        .directoryProperty()
        .convention(project.layout.buildDirectory.dir("cache/dependency-updates"))

    @get:Internal
    private val gradleVersionsUrl = project
        .findProperty("caupain.gradleVersionsUrl")
        ?.toString()
        ?: GradleConstants.DEFAULT_GRADLE_VERSIONS_URL


    private var policy: Policy? = null

    init {
        group = "verification"
        description = "Check for dependency updates"
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun checkUpdates() {
        val policy = this.policy?.let { SinglePolicy(it) } ?: StabilityLevelPolicy
        val configuration = createConfiguration(policy.name)
        val formatters = buildList {
            formatterOutputs.get().mapTo(this) { output ->
                when (output) {
                    is OutputsHandler.Output.Console ->
                        ConsoleFormatter(ConsolePrinterAdapter(logger))

                    is OutputsHandler.Output.File -> output.createFormatter()
                }
            }
            if (customFormatter.isPresent) add(customFormatter.get())
        }
        val checker = DependencyUpdateChecker(
            configuration = configuration,
            logger = LoggerAdapter(logger),
            selfUpdateResolver = PluginUpdateResolver,
            policies = listOf(policy),
            currentGradleVersion = GradleVersion.current().version,
            gradleVersionsUrl = gradleVersionsUrl,
        )
        val updates = runBlocking {
            val updates = checker.checkForUpdates()
            for (formatter in formatters) {
                formatter.format(
                    Input(
                        updateResult = updates,
                        showVersionReferences = showVersionReferences.get()
                    )
                )
            }
            updates
        }
        replacerInputFile
            .get()
            .asFile
            .outputStream()
            .use { os ->
                DefaultJson.encodeToStream(
                    value = DependencyVersionsReplacer.Input(updates),
                    stream = os
                )
            }
    }

    /**
     * Sets the update policy to use for the task.
     */
    fun selectIf(policy: Policy) {
        this.policy = policy
    }

    /**
     * Sets the update policy to use for the task.
     */
    fun selectIf(policy: com.deezer.caupain.model.Policy) {
        selectIf(Policy { policy.select(it.dependency, it.currentVersion, it.updatedVersion) })
    }

    /**
     * Sets the update policy to use for the task.
     */
    fun selectIf(policy: VersionUpdateInfo.() -> Boolean) {
        selectIf(Policy { it.policy() })
    }

    /**
     * Sets a custom formatter
     */
    fun customFormatter(formatter: Formatter) {
        customFormatter.set(formatter)
    }

    private fun createConfiguration(policyId: String): Configuration {
        return Configuration(
            repositories = repositories.get(),
            pluginRepositories = pluginRepositories.get(),
            versionCatalogPaths = versionCatalogFiles.map { it.toOkioPath() },
            excludedKeys = excludedKeys.get(),
            excludedLibraries = excludedLibraries.get(),
            excludedPlugins = excludedPluginIds.get().map { PluginExclusion(it) },
            policy = policyId,
            cacheDir = if (useCache.get()) cacheDir.get().toOkioPath() else null,
            debugHttpCalls = true,
            onlyCheckStaticVersions = onlyCheckStaticVersions.get(),
            gradleStabilityLevel = gradleStabilityLevel.get(),
            checkIgnored = checkIgnored.get(),
            searchReleaseNote = searchReleaseNote.getOrElse(false),
            githubToken = githubToken.orNull
        )
    }

    private class ConsolePrinterAdapter(private val logger: Logger) : ConsolePrinter {
        override fun print(message: String) {
            logger.lifecycle(message)
        }

        override fun printError(message: String) {
            logger.error(message)
        }
    }

    private class LoggerAdapter(private val logger: Logger) : com.deezer.caupain.model.Logger {
        override fun debug(message: String) {
            logger.debug(message)
        }

        override fun info(message: String) {
            logger.info(message)
        }

        override fun lifecycle(message: String) {
            logger.lifecycle(message)
        }

        override fun warn(message: String, throwable: Throwable?) {
            logger.warn(message, throwable)
        }

        override fun error(message: String, throwable: Throwable?) {
            logger.error(message, throwable)
        }
    }

    private class SinglePolicy(private val policy: Policy) :
        com.deezer.caupain.model.Policy {

        override val name: String = UUID.randomUUID().toString()

        override fun select(
            dependency: Dependency,
            currentVersion: Version.Resolved,
            updatedVersion: GradleDependencyVersion.Static
        ): Boolean {
            return policy.select(VersionUpdateInfo(dependency, currentVersion, updatedVersion))
        }
    }

    internal companion object {
        internal const val SERIALIZED_UPDATES_FILE_NAME = "updates_for_replacement.json"
    }
}