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
import com.deezer.caupain.model.Configuration
import com.deezer.caupain.model.Dependency
import com.deezer.caupain.model.GradleDependencyVersion
import com.deezer.caupain.model.LibraryExclusion
import com.deezer.caupain.model.PluginExclusion
import com.deezer.caupain.model.Repository
import com.deezer.caupain.model.gradle.GradleConstants
import com.deezer.caupain.model.versionCatalog.Version
import com.deezer.caupain.plugin.internal.listProperty
import com.deezer.caupain.plugin.internal.property
import com.deezer.caupain.plugin.internal.setProperty
import com.deezer.caupain.plugin.internal.toOkioPath
import kotlinx.coroutines.runBlocking
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.util.GradleVersion
import org.gradle.work.DisableCachingByDefault
import java.util.UUID

/**
 * Dependencies Update task.
 */
@DisableCachingByDefault(because = "Will never be up to date")
open class DependenciesReplaceTask : DefaultTask() {

    @get:Internal
    val repositories = project.objects.listProperty<Repository>()

    @get:Internal
    val pluginRepositories = project.objects.listProperty<Repository>()

    /**
     * @see DependenciesUpdateExtension.versionCatalogFile
     */
    @get:[InputFile OutputFile]
    val versionCatalogFile: RegularFileProperty = project.objects.fileProperty()

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

    /**
     * @see DependenciesUpdateExtension.useCache
     */
    @get:Internal
    val useCache = project.objects.property<Boolean>()

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
        description = "Replace dependencies in-place with their latest versions."
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun replaceDependencies() {
        val policy = this.policy?.let { SinglePolicy(it) }
        val configuration = createConfiguration(policy?.name)
        val checker = DependencyUpdateChecker(
            configuration = configuration,
            logger = LoggerAdapter(logger),
            selfUpdateResolver = PluginUpdateResolver,
            policies = policy?.let { listOf(it) },
            currentGradleVersion = GradleVersion.current().version,
            gradleVersionsUrl = gradleVersionsUrl,
        )
        val replacer = DependencyVersionsReplacer()
        runBlocking {
            val updates = checker.checkForUpdates()
            replacer.replaceVersions(
                versionCatalogPath = versionCatalogFile.get().toOkioPath(),
                updateResult = updates
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

    private fun createConfiguration(policyId: String?): Configuration {
        return Configuration(
            repositories = repositories.get(),
            pluginRepositories = pluginRepositories.get(),
            versionCatalogPaths = listOf(versionCatalogFile.get().toOkioPath()),
            excludedKeys = excludedKeys.get(),
            excludedLibraries = excludedLibraries.get(),
            excludedPlugins = excludedPluginIds.get().map { PluginExclusion(it) },
            policy = policyId,
            cacheDir = if (useCache.get()) cacheDir.get().toOkioPath() else null,
            debugHttpCalls = true,
        )
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
}