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

import com.deezer.caupain.DependencyUpdateChecker.Companion.CLEANING_CACHE_TASK
import com.deezer.caupain.DependencyUpdateChecker.Companion.DONE
import com.deezer.caupain.DependencyUpdateChecker.Companion.FINDING_UPDATES_TASK
import com.deezer.caupain.DependencyUpdateChecker.Companion.GATHERING_INFO_TASK
import com.deezer.caupain.DependencyUpdateChecker.Companion.PARSING_TASK
import com.deezer.caupain.internal.DefaultFileSystem
import com.deezer.caupain.internal.FileStorage
import com.deezer.caupain.internal.IODispatcher
import com.deezer.caupain.internal.configureKtorEngine
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
import com.deezer.caupain.model.SelfUpdateInfo
import com.deezer.caupain.model.UpdateInfo
import com.deezer.caupain.model.gradle.GradleConstants
import com.deezer.caupain.model.isExcluded
import com.deezer.caupain.model.loadPolicies
import com.deezer.caupain.model.maven.MavenInfo
import com.deezer.caupain.model.versionCatalog.Version
import com.deezer.caupain.resolver.DefaultUpdatedVersionResolver
import com.deezer.caupain.resolver.GithubReleaseNoteResolver
import com.deezer.caupain.resolver.GradleVersionResolver
import com.deezer.caupain.resolver.MavenInfoResolver
import com.deezer.caupain.resolver.SelfUpdateResolver
import com.deezer.caupain.resolver.UpdatedVersionResolver
import com.deezer.caupain.serialization.DefaultJson
import com.deezer.caupain.serialization.DefaultToml
import com.deezer.caupain.serialization.xml.DefaultXml
import dev.drewhamilton.poko.Poko
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.cache.InvalidCacheStateException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.jsonIo
import io.ktor.serialization.kotlinx.serialization
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.ExperimentalSerializationApi
import okio.FileSystem
import okio.IOException
import okio.Path

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
     * Available policies
     */
    public val policies: Sequence<Policy>

    /**
     * The version resolver used to find updated versions of dependencies.
     */
    public val versionResolver: UpdatedVersionResolver

    /**
     * The HTTP client used to make requests to repositories and other services.
     */
    public val httpClient: HttpClient

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
        @Poko
        public class Indeterminate(override val taskName: String) : Progress

        /**
         * Determinate progress, used when the task length is known.
         */
        @Poko
        public class Determinate(
            override val taskName: String,
            public val percentage: Int,
        ) : Progress
    }

    public companion object {
        internal const val PARSING_TASK = "Parsing version catalog"
        internal const val CLEANING_CACHE_TASK = "Cleaning cache"
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
@OptIn(ExperimentalSerializationApi::class)
@Suppress("LongParameterList") // Needed to reflect parameters
public fun DependencyUpdateChecker(
    configuration: Configuration,
    currentGradleVersion: String?,
    logger: Logger = Logger.EMPTY,
    selfUpdateResolver: SelfUpdateResolver? = null,
    fileSystem: FileSystem = DefaultFileSystem,
    ioDispatcher: CoroutineDispatcher = IODispatcher,
    policies: List<Policy>? = null,
    gradleVersionsUrl: String = GradleConstants.DEFAULT_GRADLE_VERSIONS_URL,
): DependencyUpdateChecker = DefaultDependencyUpdateChecker(
    configuration = configuration,
    currentGradleVersion = currentGradleVersion,
    fileSystem = fileSystem,
    httpClient = HttpClient {
        engine {
            configureKtorEngine()
        }
        install(ContentNegotiation) {
            jsonIo(DefaultJson)
            serialization(ContentType.Any, DefaultXml)
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
        ioDispatcher = IODispatcher
    ),
    logger = logger,
    selfUpdateResolver = selfUpdateResolver,
    policies = policies,
    gradleVersionsUrl = gradleVersionsUrl
)

@Suppress("LongParameterList") // Needed to reflect parameters
internal class DefaultDependencyUpdateChecker(
    private val configuration: Configuration,
    private val currentGradleVersion: String?,
    override val httpClient: HttpClient,
    private val fileSystem: FileSystem,
    ioDispatcher: CoroutineDispatcher,
    private val versionCatalogParser: VersionCatalogParser,
    private val logger: Logger,
    private val selfUpdateResolver: SelfUpdateResolver?,
    policies: List<Policy>?,
    gradleVersionsUrl: String = GradleConstants.DEFAULT_GRADLE_VERSIONS_URL,
) : DependencyUpdateChecker {

    override val policies = policies?.asSequence() ?: sequence {
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

    private val selectedPolicies by lazy {
        configuration
            .policies
            .map { name ->
                policies?.firstOrNull { it.name == name } ?: throw UnknownPolicyException(name)
            }
    }

    private val infoResolver = MavenInfoResolver(
        httpClient = httpClient,
        ioDispatcher = ioDispatcher,
        logger = logger
    )

    override val versionResolver by lazy {
        DefaultUpdatedVersionResolver(
            httpClient = httpClient,
            repositories = configuration.repositories,
            pluginRepositories = configuration.pluginRepositories,
            logger = logger,
            onlyCheckStaticVersions = configuration.onlyCheckStaticVersions,
            policies = selectedPolicies,
            ioDispatcher = ioDispatcher,
            verifyExistence = configuration.verifyExistence,
            mavenInfoResolver = infoResolver,
        )
    }

    private val gradleVersionResolver = GradleVersionResolver(
        httpClient = httpClient,
        logger = logger,
        gradleVersionsUrl = gradleVersionsUrl,
        stabilityLevel = configuration.gradleStabilityLevel,
        ioDispatcher = ioDispatcher
    )

    private val releaseNoteResolver = GithubReleaseNoteResolver(
        httpClient = httpClient,
        ioDispatcher = ioDispatcher,
        logger = logger,
        githubToken = configuration.githubToken
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
        val cacheDir = configuration.cacheDir
        if (cacheDir != null && configuration.cleanCache) {
            progressFlow.value = DependencyUpdateChecker.Progress.Indeterminate(CLEANING_CACHE_TASK)
            try {
                fileSystem.deleteRecursively(cacheDir)
            } catch (_: IOException) {
                // Ignored
            }
        }
        val updatedVersionsResult: UpdateVersionResult
        val updatesInfos = try {
            completed = 0
            progressFlow.value =
                DependencyUpdateChecker.Progress.Determinate(FINDING_UPDATES_TASK, 0)
            updatedVersionsResult = checkUpdatedVersions(versionCatalogParseResults)
            completed = 0
            checkUpdateInfo(updatedVersionsResult.updatedVersions)
        } catch (e: InvalidCacheStateException) {
            throw CorruptedCacheException(
                cacheDir = fileSystem.canonicalize(requireNotNull(configuration.cacheDir)),
                cause = e,
            )
        }
        // Sort and return
        return DependenciesUpdateResult(
            gradleUpdateInfo = if (updatedVersionsResult.updatedGradleVersion != null && currentGradleVersion != null) {
                GradleUpdateInfo(
                    currentVersion = currentGradleVersion,
                    updatedVersion = updatedVersionsResult.updatedGradleVersion
                )
            } else {
                null
            },
            updateInfos = buildMap {
                for (type in UpdateInfo.Type.entries) {
                    put(type, updatesInfos[type]?.sortedBy { it.dependencyId }.orEmpty())
                }
            },
            ignoredUpdateInfos = updatedVersionsResult
                .ignoredVersions
                .sortedBy { it.dependencyId },
            versionCatalog = versionCatalogParseResults.singleOrNull()?.versionCatalog,
            selfUpdateInfo = updatedVersionsResult.selfUpdateInfo,
            versionCatalogInfo = versionCatalogParseResults.singleOrNull()?.info
        )
    }

    @Suppress("LongMethod")
    private suspend fun checkUpdatedVersions(parseResults: List<VersionCatalogParseResult>): UpdateVersionResult {
        val checkGradleUpdate = currentGradleVersion != null
        val checkSelfUpdate = selfUpdateResolver != null
        val updatedVersionsMutex = Mutex()
        var updatedGradleVersion: String? = null
        var selfUpdateInfo: SelfUpdateInfo? = null
        val updatedVersions = mutableListOf<DependencyUpdateResult>()
        val ignoredVersions = mutableListOf<UpdateInfo>()
        var nbDependencies = parseResults.sumOf { it.versionCatalog.dependencies.size }
        if (checkGradleUpdate) nbDependencies++
        if (checkSelfUpdate) nbDependencies++
        coroutineScope {
            parseResults
                .asSequence()
                .flatMap { (versionCatalog, info) ->
                    versionCatalog
                        .dependencies
                        .asSequence()
                        .map { (key, dep) ->
                            async {
                                val isExcluded = configuration.isExcluded(key, dep)
                                        || info.ignores.isExcluded(key, dep)
                                if (!isExcluded || configuration.checkIgnored) {
                                    val updatedVersion = versionResolver.getUpdatedVersion(
                                        dep,
                                        versionCatalog.versions
                                    )
                                    if (updatedVersion != null) {
                                        logger.info("Found updated version ${updatedVersion.updatedVersion} for ${dep.moduleId}")
                                        updatedVersionsMutex.withLock {
                                            val result = DependencyUpdateResult(
                                                dependencyKey = key,
                                                dependency = dep,
                                                repository = updatedVersion.repository,
                                                currentVersion = updatedVersion.currentVersion,
                                                updatedVersion = updatedVersion.updatedVersion
                                            )
                                            if (isExcluded) {
                                                ignoredVersions.add(
                                                    toUpdateInfo(
                                                        key = key,
                                                        dependency = dep,
                                                        currentVersion = updatedVersion.currentVersion,
                                                        updatedVersion = updatedVersion.updatedVersion
                                                    )
                                                )
                                            } else {
                                                updatedVersions.add(result)
                                            }
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
                .plus(
                    async {
                        if (selfUpdateResolver != null) {
                            logger.info("Finding self update info")
                            selfUpdateInfo = selfUpdateResolver.resolveSelfUpdate(
                                this@DefaultDependencyUpdateChecker,
                                parseResults.map { it.versionCatalog }
                            )
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
            ignoredVersions = ignoredVersions,
            updatedGradleVersion = updatedGradleVersion,
            selfUpdateInfo = selfUpdateInfo
        )
    }

    private suspend fun checkUpdateInfo(updatedVersions: List<DependencyUpdateResult>): Map<UpdateInfo.Type, List<UpdateInfo>> {
        val nbUpdatedVersions = updatedVersions.size
        val nbSteps = if (configuration.searchReleaseNote) {
            nbUpdatedVersions * 2
        } else {
            nbUpdatedVersions
        }
        // Search Maven infos
        val mavenInfos = getMavenInfos(updatedVersions) {
            val percentage = ++completed * 50 / nbSteps + 50
            progressFlow.value = DependencyUpdateChecker.Progress.Determinate(
                taskName = GATHERING_INFO_TASK,
                percentage = percentage
            )
        }
        // Search release notes and build final infos
        val updatesInfos = mutableMapOf<UpdateInfo.Type, MutableList<UpdateInfo>>()
        for (result in updatedVersions) {
            logger.info("Finding release note info for ${result.dependency.moduleId}")
            val type = when (result.dependency) {
                is Dependency.Library -> UpdateInfo.Type.LIBRARY
                is Dependency.Plugin -> UpdateInfo.Type.PLUGIN
            }
            val mavenInfo = mavenInfos[result.dependencyKey]
            val releaseNoteUrl = if (configuration.searchReleaseNote) {
                releaseNoteResolver.getReleaseNoteUrl(
                    mavenInfo = mavenInfo,
                    updatedVersion = result.updatedVersion
                )
            } else {
                null
            }
            val updateInfo = toUpdateInfo(
                key = result.dependencyKey,
                dependency = result.dependency,
                currentVersion = result.currentVersion,
                updatedVersion = result.updatedVersion,
                mavenInfo = mavenInfo,
                releaseNoteUrl = releaseNoteUrl
            )
            val infosForType = updatesInfos[type]
                ?: mutableListOf<UpdateInfo>().also { updatesInfos[type] = it }
            infosForType.add(updateInfo)
            val percentage = ++completed * 50 / nbSteps + 50
            progressFlow.value = DependencyUpdateChecker.Progress.Determinate(
                taskName = GATHERING_INFO_TASK,
                percentage = percentage
            )
        }
        progressFlow.value = DependencyUpdateChecker.Progress.Determinate(DONE, 100)
        return updatesInfos
    }

    private suspend inline fun getMavenInfos(
        updatedVersions: List<DependencyUpdateResult>,
        crossinline onStepFinished: () -> Unit,
    ): Map<String, MavenInfo> {
        val mavenInfoMutex = Mutex()
        val mavenInfos = mutableMapOf<String, MavenInfo>()
        coroutineScope {
            updatedVersions
                .map { result ->
                    async {
                        logger.info("Finding Maven info for ${result.dependency.moduleId}")
                        val mavenInfo = infoResolver.getMavenInfo(
                            dependency = result.dependency,
                            repository = result.repository,
                            updatedVersion = result.updatedVersion
                        )
                        if (mavenInfo != null) {
                            mavenInfoMutex.withLock {
                                mavenInfos[result.dependencyKey] = mavenInfo
                            }
                        }
                        onStepFinished()
                    }
                }
                .awaitAll()
        }
        return mavenInfos
    }

    private fun toUpdateInfo(
        key: String,
        dependency: Dependency,
        currentVersion: Version.Resolved,
        updatedVersion: GradleDependencyVersion.Static,
        mavenInfo: MavenInfo? = null,
        releaseNoteUrl: String? = null,
    ) = UpdateInfo(
        dependency = key,
        dependencyId = dependency.moduleId,
        name = mavenInfo?.name,
        url = mavenInfo?.url,
        releaseNoteUrl = releaseNoteUrl,
        currentVersion = currentVersion,
        updatedVersion = updatedVersion
    )

    private data class UpdateVersionResult(
        val updatedVersions: List<DependencyUpdateResult>,
        val updatedGradleVersion: String?,
        val selfUpdateInfo: SelfUpdateInfo?,
        val ignoredVersions: List<UpdateInfo>
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

/**
 * Exception thrown when an unknown policy is specified in the configuration.
 */
public class UnknownPolicyException(name: String) :
    CaupainException("Unknown policy: $name")

/**
 * Exception thrown when the cache directory is corrupted.
 */
public class CorruptedCacheException(
    cacheDir: Path,
    cause: Throwable,
) : CaupainException(
    message = "Cache directory $cacheDir is corrupted. Please delete it and try again.",
    cause = cause,
)
