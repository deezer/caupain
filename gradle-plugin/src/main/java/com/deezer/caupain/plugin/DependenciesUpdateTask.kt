package com.deezer.caupain.plugin

import com.deezer.caupain.DependencyUpdateChecker
import com.deezer.caupain.formatting.console.ConsoleFormatter
import com.deezer.caupain.formatting.console.ConsolePrinter
import com.deezer.caupain.formatting.html.HtmlFormatter
import com.deezer.caupain.model.Configuration
import com.deezer.caupain.model.GradleDependencyVersion
import com.deezer.caupain.model.LibraryExclusion
import com.deezer.caupain.model.PluginExclusion
import com.deezer.caupain.model.versionCatalog.Version
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toOkioPath
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logger
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.setProperty
import org.gradle.util.GradleVersion
import java.util.UUID

/**
 * Dependencies Update task.
 */
abstract class DependenciesUpdateTask : DefaultTask() {

    @get:Nested
    abstract val repositoryHandler: RepositoryHandler

    /**
     * @see DependenciesUpdateExtension.versionCatalogFile
     */
    @get:InputFile
    val versionCatalogFile: RegularFileProperty = project.objects.fileProperty()

    /**
     * @see DependenciesUpdateExtension.excludedKeys
     */
    @get:Input
    val excludedKeys = project.objects.setProperty<String>()

    /**
     * @see DependenciesUpdateExtension.excludedLibraries
     */
    @get:Input
    val excludedLibraries = project.objects.listProperty<LibraryExclusion>()

    /**
     * @see DependenciesUpdateExtension.excludedPluginIds
     */
    @get:Input
    val excludedPluginIds = project.objects.setProperty<String>()

    /**
     * @see DependenciesUpdateExtension.outputFile
     */
    @OutputFile
    val outputFile: RegularFileProperty = project.objects.fileProperty()

    /**
     * @see DependenciesUpdateExtension.outputToConsole
     */
    @get:Input
    val outputToConsole = project.objects.property<Boolean>()

    /**
     * @see DependenciesUpdateExtension.outputToFile
     */
    @get:Input
    val outputToFile = project.objects.property<Boolean>()

    /**
     * @see DependenciesUpdateExtension.useCache
     */
    @get:Input
    val useCache = project.objects.property<Boolean>()

    /**
     * @see DependenciesUpdateExtension.onlyCheckStaticVersions
     */
    @get:Input
    val onlyCheckStaticVersions = project.objects.property<Boolean>()

    /**
     * The cache directory for the HTTP cache. Default is "build/cache/dependency-updates".
     */
    @get:Internal
    val cacheDir: DirectoryProperty = project
        .objects
        .directoryProperty()
        .convention(project.layout.buildDirectory.dir("cache/dependency-updates"))

    @get:Input
    val gradleCurrentVersionUrl: Property<String> = project
        .objects
        .property<String>()
        .convention(Configuration.DEFAULT_GRADLE_VERSION_URL)

    private var policy: Policy? = null

    init {
        group = "verification"
        description = "Check for dependency updates"
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun checkUpdates() {
        val policy = this.policy?.let { SinglePolicy(it) }
        val configuration = createConfiguration(policy?.name)
        val formatters = buildList {
            if (outputToConsole.get()) add(ConsoleFormatter(ConsolePrinterAdapter(logger)))
            if (outputToFile.get()) add(HtmlFormatter(outputFile.get().asFile.toOkioPath()))
        }
        val checker = DependencyUpdateChecker(
            configuration = configuration,
            logger = LoggerAdapter(logger),
            policies = policy?.let { listOf(it) },
            currentGradleVersion = GradleVersion.current().version,
        )
        runBlocking {
            val updates = checker.checkForUpdates()
            for (formatter in formatters) {
                formatter.format(updates)
            }
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
        selectIf(Policy { policy.select(currentVersion, updatedVersion) })
    }

    /**
     * Sets the update policy to use for the task.
     */
    fun selectIf(policy: VersionUpdateInfo.() -> Boolean) {
        selectIf(Policy { policy() })
    }

    /**
     * Configures repositories
     */
    fun repositories(action: Action<RepositoryHandler>) {
        action.execute(repositoryHandler)
    }

    private fun createConfiguration(policyId: String?): Configuration = Configuration(
        repositories = repositoryHandler.libraries.get(),
        pluginRepositories = repositoryHandler.plugins.get(),
        versionCatalogPath = versionCatalogFile.asFile.get().toOkioPath(),
        excludedKeys = excludedKeys.get(),
        excludedLibraries = excludedLibraries.get(),
        excludedPlugins = excludedPluginIds.get().map { PluginExclusion(it) },
        policy = policyId,
        cacheDir = if (useCache.get()) {
            cacheDir.asFile.get().toOkioPath()
        } else {
            null
        },
        debugHttpCalls = true,
        gradleCurrentVersionUrl = gradleCurrentVersionUrl.get(),
        onlyCheckStaticVersions = onlyCheckStaticVersions.get()
    )

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
            currentVersion: Version.Resolved,
            updatedVersion: GradleDependencyVersion.Static
        ): Boolean {
            return policy.select(VersionUpdateInfo(currentVersion, updatedVersion))
        }
    }
}