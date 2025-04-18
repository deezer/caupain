package com.deezer.dependencies.plugin

import com.deezer.dependencies.DefaultDependencyUpdateChecker
import com.deezer.dependencies.formatting.console.ConsoleFormatter
import com.deezer.dependencies.formatting.console.ConsolePrinter
import com.deezer.dependencies.formatting.html.HtmlFormatter
import com.deezer.dependencies.model.Configuration
import com.deezer.dependencies.model.GradleDependencyVersion
import com.deezer.dependencies.model.LibraryExclusion
import com.deezer.dependencies.model.PluginExclusion
import com.deezer.dependencies.model.Repository
import com.deezer.dependencies.model.versionCatalog.Version
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toOkioPath
import org.gradle.api.DefaultTask
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.setProperty
import java.util.UUID

open class DependenciesUpdateTask : DefaultTask() {

    @get:Input
    val repositories = project.objects.listProperty<Repository>()

    @get:Input
    val pluginRepositories = project.objects.listProperty<Repository>()

    @get:InputFile
    val versionCatalogFile = project.objects.fileProperty()

    @get:Input
    val excludedKeys = project.objects.setProperty<String>()

    @get:Input
    val excludedLibraries = project.objects.listProperty<LibraryExclusion>()

    @get:Input
    val excludedPluginIds = project.objects.setProperty<String>()

    @OutputFile
    val outputFile = project.objects.fileProperty()

    @get:Input
    val outputToConsole = project.objects.property<Boolean>()

    @get:Input
    val outputToFile = project.objects.property<Boolean>()

    private var policy: Policy? = null

    init {
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
        val checker = DefaultDependencyUpdateChecker(
            configuration = configuration,
            logger = LoggerAdapter(logger),
            policies = policy?.let { mapOf(it.name to it) }
        )
        runBlocking {
            val updates = checker.checkForUpdates()
            for (formatter in formatters) {
                formatter.format(updates)
            }
        }
    }

    @Suppress("unused")
    fun selectIf(policy: Policy) {
        this.policy = policy
    }

    private fun createConfiguration(policyId: String?): Configuration = Configuration(
        repositories = repositories.get(),
        pluginRepositories = pluginRepositories.get(),
        versionCatalogPath = versionCatalogFile.asFile.get().toOkioPath(),
        excludedKeys = excludedKeys.get(),
        excludedLibraries = excludedLibraries.get(),
        excludedPlugins = excludedPluginIds.get().map { PluginExclusion(it) },
        policy = policyId
    )

    private class ConsolePrinterAdapter(private val logger: Logger) : ConsolePrinter {
        override fun print(message: String) {
            logger.info(message)
        }

        override fun printError(message: String) {
            logger.error(message)
        }
    }

    private class LoggerAdapter(private val logger: Logger) : com.deezer.dependencies.model.Logger {
        override fun debug(message: String) {
            logger.debug(message)
        }

        override fun info(message: String) {
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
        com.deezer.dependencies.model.Policy {

        override val name: String = UUID.randomUUID().toString()

        override fun select(
            currentVersion: Version.Direct,
            updatedVersion: GradleDependencyVersion.Single
        ): Boolean {
            return policy.select(VersionUpdateInfo(currentVersion, updatedVersion))
        }
    }
}