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

package com.deezer.caupain

import com.deezer.caupain.DependencyUpdateChecker.Companion.DONE
import com.deezer.caupain.DependencyUpdateChecker.Companion.FINDING_UPDATES_TASK
import com.deezer.caupain.DependencyUpdateChecker.Companion.GATHERING_INFO_TASK
import com.deezer.caupain.DependencyUpdateChecker.Companion.PARSING_TASK
import com.deezer.caupain.internal.FileStorage
import com.deezer.caupain.internal.extension
import com.deezer.caupain.model.Configuration
import com.deezer.caupain.model.DEFAULT_POLICIES
import com.deezer.caupain.model.DependenciesUpdateResult
import com.deezer.caupain.model.Dependency
import com.deezer.caupain.model.GradleDependencyVersion
import com.deezer.caupain.model.GradleUpdateInfo
import com.deezer.caupain.model.KtorLoggerAdapter
import com.deezer.caupain.model.Logger
import com.deezer.caupain.model.Policy
import com.deezer.caupain.model.Repository
import com.deezer.caupain.model.UpdateInfo
import com.deezer.caupain.model.isExcluded
import com.deezer.caupain.model.loadPolicies
import com.deezer.caupain.model.versionCatalog.Version
import com.deezer.caupain.resolver.GradleVersionResolver
import com.deezer.caupain.resolver.UpdateInfoResolver
import com.deezer.caupain.resolver.UpdatedVersionResolver
import com.deezer.caupain.serialization.DefaultJson
import com.deezer.caupain.serialization.DefaultToml
import com.deezer.caupain.serialization.DefaultXml
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.serialization.kotlinx.xml.xml
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

/**
 * Given a version catalog, this interrogates Maven repositories (specified via [Configuration]), and
 * computes the updated versions for each dependency.
 */
public interface DependencyUpdateChecker {

    /**
     * Progress flow
     */
    public val progress: Flow<Progress?>

    /**
     * Check for updates in the version catalog.
     *
     * @return the result of the update check, containing the updated versions for each dependency
     * and updated version for Gradle.
     */
    public suspend fun checkForUpdates(): DependenciesUpdateResult

    /**
     * Progress interface to represent the progress of the update check.
     */
    public sealed interface Progress {
        /**
         * The name of the current task.
         */
        public val taskName: String

        /**
         * Intederminate progress, used when the task length is not known.
         */
        public class Indeterminate(override val taskName: String) : Progress {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other == null || this::class != other::class) return false

                other as Indeterminate

                return taskName == other.taskName
            }

            override fun hashCode(): Int {
                return taskName.hashCode()
            }

            override fun toString(): String {
                return "Indeterminate(taskName='$taskName')"
            }
        }

        /**
         * Determinate progress, used when the task length is known.
         */
        public class Determinate(
            override val taskName: String,
            public val percentage: Int,
        ) : Progress {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other == null || this::class != other::class) return false

                other as Determinate

                if (percentage != other.percentage) return false
                if (taskName != other.taskName) return false

                return true
            }

            override fun hashCode(): Int {
                var result = percentage
                result = 31 * result + taskName.hashCode()
                return result
            }

            override fun toString(): String {
                return "Determinate(taskName='$taskName', percentage=$percentage)"
            }
        }
    }

    public companion object {
        internal const val PARSING_TASK = "Parsing version catalog"
        internal const val FINDING_UPDATES_TASK = "Finding updates"
        internal const val GATHERING_INFO_TASK = "Gathering update info"
        internal const val DONE = "done"

        /**
         * The maximum length of the task name, used to format the progress output.
         */
        public val MAX_TASK_NAME_LENGTH: Int =
            listOf(
                PARSING_TASK,
                FINDING_UPDATES_TASK,
                GATHERING_INFO_TASK,
                DONE
            ).maxOf { it.length }
    }
}

/**
 * Creates a new [DependencyUpdateChecker] instance with the specified parameters.
 */
@Suppress("LongParameterList") // Needed to reflect parameters
public fun DependencyUpdateChecker(
    configuration: Configuration,
    currentGradleVersion: String?,
    logger: Logger = Logger.EMPTY,
    fileSystem: FileSystem = FileSystem.SYSTEM,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    policies: List<Policy>? = null,
): DependencyUpdateChecker = DefaultDependencyUpdateChecker(
    configuration = configuration,
    currentGradleVersion = currentGradleVersion,
    fileSystem = fileSystem,
    httpClient = HttpClient {
        install(ContentNegotiation) {
            json(DefaultJson)
            xml(DefaultXml, ContentType.Any)
        }
        install(Logging) {
            this.logger = KtorLoggerAdapter(logger)
            level = if (configuration.debugHttpCalls) LogLevel.ALL else LogLevel.INFO
            sanitizeHeader { it == HttpHeaders.Authorization }
        }
        install(HttpCache) {
            configuration.cacheDir?.let { cacheDir ->
                fileSystem.createDirectories(cacheDir)
                publicStorage(FileStorage(fileSystem, cacheDir))
            }
        }
        install(HttpRequestRetry) {
            retryOnException(maxRetries = 3, retryOnTimeout = true)
            exponentialDelay(baseDelayMs = 500)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 10_000
        }
    },
    ioDispatcher = ioDispatcher,
    versionCatalogParser = DefaultVersionCatalogParser(
        toml = DefaultToml,
        fileSystem = fileSystem,
        ioDispatcher = Dispatchers.IO
    ),
    logger = logger,
    policies = policies
)

@Suppress("LongParameterList") // Needed to reflect parameters
internal class DefaultDependencyUpdateChecker(
    private val configuration: Configuration,
    private val currentGradleVersion: String?,
    httpClient: HttpClient,
    private val fileSystem: FileSystem,
    ioDispatcher: CoroutineDispatcher,
    private val versionCatalogParser: VersionCatalogParser,
    private val logger: Logger,
    policies: List<Policy>?,
) : DependencyUpdateChecker {

    private val policies = policies?.asSequence() ?: sequence {
        yieldAll(DEFAULT_POLICIES)
        yieldAll(
            configuration
                .policyPluginsDir
                ?.takeIf { fileSystem.exists(it) }
                ?.let { fileSystem.list(it) }
                .orEmpty()
                .asSequence()
                .filter { it.extension == "jar" }
                .asIterable()
                .let { loadPolicies(it, logger) }
        )
    }

    private val policy = configuration.policy?.let { name ->
        this.policies.firstOrNull { it.name == name }
    }

    private val versionResolver = UpdatedVersionResolver(
        httpClient = httpClient,
        repositories = configuration.repositories,
        pluginRepositories = configuration.pluginRepositories,
        logger = logger,
        onlyCheckStaticVersions = configuration.onlyCheckStaticVersions,
        policy = policy,
        ioDispatcher = ioDispatcher
    )

    private val gradleVersionResolver = GradleVersionResolver(
        httpClient = httpClient,
        gradleCurrentVersionUrl = configuration.gradleCurrentVersionUrl,
        ioDispatcher = ioDispatcher
    )

    private val infoResolver = UpdateInfoResolver(
        httpClient = httpClient,
        ioDispatcher = ioDispatcher,
        logger = logger
    )

    private var completed by atomic(0)

    private val progressFlow = MutableStateFlow<DependencyUpdateChecker.Progress?>(null)

    override val progress: Flow<DependencyUpdateChecker.Progress?>
        get() = progressFlow.asStateFlow()

    override suspend fun checkForUpdates(): DependenciesUpdateResult {
        val versionCatalogPaths = configuration
            .versionCatalogPaths
            .filter { fileSystem.exists(it) }
            .toList()
        if (versionCatalogPaths.isEmpty()) {
            throw NoVersionCatalogException(configuration.versionCatalogPaths)
        }
        val versionCatalogPathStrings = versionCatalogPaths
            .map { fileSystem.canonicalize(it) }
            .joinToString()
        logger.info("Parsing version catalogs from $versionCatalogPathStrings}")
        progressFlow.value = DependencyUpdateChecker.Progress.Indeterminate(PARSING_TASK)
        val versionCatalogParseResults = versionCatalogPaths
            .map { versionCatalogParser.parseDependencyInfo(it) }
        completed = 0
        progressFlow.value =
            DependencyUpdateChecker.Progress.Determinate(FINDING_UPDATES_TASK, 0)
        val (updatedVersions, updatedGradleVersion) = checkUpdatedVersions(
            versionCatalogParseResults
        )
        completed = 0
        val updatesInfos = checkUpdateInfo(updatedVersions)
        // Sort and return
        return DependenciesUpdateResult(
            gradleUpdateInfo = if (updatedGradleVersion != null && currentGradleVersion != null) {
                GradleUpdateInfo(
                    currentVersion = currentGradleVersion,
                    updatedVersion = updatedGradleVersion
                )
            } else {
                null
            },
            updateInfos = buildMap {
                for (type in UpdateInfo.Type.entries) {
                    put(type, updatesInfos[type]?.sortedBy { it.dependencyId }.orEmpty())
                }
            }
        )
    }

    private suspend fun checkUpdatedVersions(parseResults: List<VersionCatalogParseResult>): UpdateVersionResult {
        val checkGradleUpdate = currentGradleVersion != null
        val updatedVersionsMutex = Mutex()
        var updatedGradleVersion: String? = null
        val updatedVersions = mutableListOf<DependencyUpdateResult>()
        val nbDependencies =
            parseResults.sumOf { it.versionCatalog.dependencies.size } + if (checkGradleUpdate) 1 else 0
        coroutineScope {
            parseResults
                .asSequence()
                .flatMap { (versionCatalog, ignores) ->
                    versionCatalog
                        .dependencies
                        .asSequence()
                        .map { (key, dep) ->
                            async {
                                if (
                                    !configuration.isExcluded(key, dep)
                                    && !ignores.isExcluded(key, dep)
                                ) {
                                    val updatedVersion = versionResolver.getUpdatedVersion(
                                        dep,
                                        versionCatalog.versions
                                    )
                                    if (updatedVersion != null) {
                                        logger.info("Found updated version ${updatedVersion.updatedVersion} for ${dep.moduleId}")
                                        updatedVersionsMutex.withLock {
                                            updatedVersions.add(
                                                DependencyUpdateResult(
                                                    dependencyKey = key,
                                                    dependency = dep,
                                                    repository = updatedVersion.repository,
                                                    currentVersion = updatedVersion.currentVersion,
                                                    updatedVersion = updatedVersion.updatedVersion
                                                )
                                            )
                                        }
                                    }
                                }
                                val percentage = ++completed * 50 / nbDependencies
                                progressFlow.value = DependencyUpdateChecker.Progress.Determinate(
                                    taskName = FINDING_UPDATES_TASK,
                                    percentage = percentage
                                )
                            }
                        }
                }
                .plus(
                    async {
                        if (currentGradleVersion != null) {
                            logger.info("Finding updated Gradle version")
                            updatedGradleVersion = gradleVersionResolver
                                .getUpdatedVersion(currentGradleVersion)
                                ?.also { logger.debug("Found updated Gradle version $it") }
                            val percentage = ++completed * 50 / nbDependencies
                            progressFlow.value = DependencyUpdateChecker.Progress.Determinate(
                                taskName = FINDING_UPDATES_TASK,
                                percentage = percentage
                            )
                        }
                    }
                )
                .toList()
                .awaitAll()
        }
        return UpdateVersionResult(
            updatedVersions = updatedVersions,
            updatedGradleVersion = updatedGradleVersion
        )
    }

    private suspend fun checkUpdateInfo(updatedVersions: List<DependencyUpdateResult>): Map<UpdateInfo.Type, List<UpdateInfo>> {
        val nbUpdatedVersions = updatedVersions.size
        val updateInfosMutex = Mutex()
        val updatesInfos = mutableMapOf<UpdateInfo.Type, MutableList<UpdateInfo>>()
        coroutineScope {
            updatedVersions
                .map { result ->
                    async {
                        logger.info("Finding Maven info for ${result.dependency.moduleId}")
                        val (type, updateInfo) = infoResolver.getUpdateInfo(
                            key = result.dependencyKey,
                            dependency = result.dependency,
                            repository = result.repository,
                            currentVersion = result.currentVersion,
                            updatedVersion = result.updatedVersion
                        )
                        updateInfosMutex.withLock {
                            val infosForType = updatesInfos[type]
                                ?: mutableListOf<UpdateInfo>().also { updatesInfos[type] = it }
                            infosForType.add(updateInfo)
                        }
                        val percentage = ++completed * 50 / nbUpdatedVersions + 50
                        progressFlow.value = DependencyUpdateChecker.Progress.Determinate(
                            taskName = GATHERING_INFO_TASK,
                            percentage = percentage
                        )
                    }
                }
                .awaitAll()
            progressFlow.value = DependencyUpdateChecker.Progress.Determinate(DONE, 100)
        }
        return updatesInfos
    }

    private data class UpdateVersionResult(
        val updatedVersions: List<DependencyUpdateResult>,
        val updatedGradleVersion: String?
    )

    private data class DependencyUpdateResult(
        val dependencyKey: String,
        val dependency: Dependency,
        val repository: Repository,
        val currentVersion: Version.Resolved,
        val updatedVersion: GradleDependencyVersion.Static,
    )
}

/**
 * Exception thrown when no version catalog is not found at the specified path.
 *
 * @param paths The paths to the version catalogs.
 */
public class NoVersionCatalogException(paths: Iterable<Path>) :
    CaupainException("No version catalog found at ${paths.joinToString()}")

/**
 * Exception thrown when multiple policies have the same name.
 */
public class SamePolicyNameException(name: String) :
    CaupainException("Policy name conflict: multiple polices have the same name $name")