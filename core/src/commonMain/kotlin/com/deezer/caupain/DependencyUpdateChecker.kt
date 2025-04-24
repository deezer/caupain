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
import com.deezer.caupain.model.GradleVersion
import com.deezer.caupain.model.Logger
import com.deezer.caupain.model.Policy
import com.deezer.caupain.model.PolicyLoader
import com.deezer.caupain.model.Repository
import com.deezer.caupain.model.UpdateInfo
import com.deezer.caupain.model.isExcluded
import com.deezer.caupain.model.maven.MavenInfo
import com.deezer.caupain.model.maven.Metadata
import com.deezer.caupain.model.versionCatalog.Version
import com.deezer.caupain.serialization.DefaultJson
import com.deezer.caupain.serialization.DefaultToml
import com.deezer.caupain.serialization.DefaultXml
import io.github.z4kn4fein.semver.VersionFormatException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.isSuccess
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
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import io.github.z4kn4fein.semver.Version as SemanticVersion

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
        public data class Indeterminate(override val taskName: String) : Progress

        /**
         * Determinate progress, used when the task length is known.
         */
        public data class Determinate(
            override val taskName: String,
            val percentage: Int,
        ) : Progress
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
@Suppress("LongParameterList")
public fun DependencyUpdateChecker(
    configuration: Configuration,
    currentGradleVersion: String?,
    logger: Logger = Logger.EMPTY,
    fileSystem: FileSystem = FileSystem.SYSTEM,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    policies: Map<String, Policy>? = null
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
            this.logger = logger
            level = if (configuration.debugHttpCalls) LogLevel.ALL else LogLevel.INFO
            sanitizeHeader { it == HttpHeaders.Authorization }
        }
        install(HttpCache) {
            configuration.cacheDir?.let { cacheDir ->
                fileSystem.createDirectories(cacheDir)
                publicStorage(FileStorage(fileSystem, cacheDir))
            }
        }
    },
    ioDispatcher = ioDispatcher,
    versionCatalogParser = DefaultVersionCatalogParser(
        toml = DefaultToml,
        versionCatalogPath = configuration.versionCatalogPath,
        fileSystem = fileSystem,
        ioDispatcher = Dispatchers.IO
    ),
    logger = logger,
    policies = policies
)

@Suppress("LongParameterList")
internal class DefaultDependencyUpdateChecker(
    private val configuration: Configuration,
    private val currentGradleVersion: String?,
    private val httpClient: HttpClient,
    private val fileSystem: FileSystem,
    private val ioDispatcher: CoroutineDispatcher,
    private val versionCatalogParser: VersionCatalogParser,
    private val logger: Logger,
    policies: Map<String, Policy>?,
) : DependencyUpdateChecker {

    private val policies by lazy {
        policies ?: buildMap {
            putAll(DEFAULT_POLICIES)
            configuration
                .policyPluginsDir
                ?.takeIf { fileSystem.exists(it) }
                ?.let { fileSystem.list(it) }
                .orEmpty()
                .asSequence()
                .filter { it.extension == "jar" }
                .asIterable()
                .let { PolicyLoader.loadPolicies(it) }
                .associateByTo(this) { it.name }
        }
    }

    private val policy by lazy {
        configuration.policy?.let { this.policies[it] }
    }

    private var completed by atomic(0)

    private val progressFlow = MutableStateFlow<DependencyUpdateChecker.Progress?>(null)

    override val progress: Flow<DependencyUpdateChecker.Progress?>
        get() = progressFlow.asStateFlow()

    @Suppress("LongMethod", "CyclomaticComplexMethod") // This cannot be easily simplified because of the async blocks
    override suspend fun checkForUpdates(): DependenciesUpdateResult {
        if (!fileSystem.exists(configuration.versionCatalogPath)) {
            throw NoVersionCatalogException(configuration.versionCatalogPath)
        }
        logger.info("Parsing version catalog path from ${fileSystem.canonicalize(configuration.versionCatalogPath)}")
        progressFlow.value = DependencyUpdateChecker.Progress.Indeterminate(PARSING_TASK)
        val versionCatalog = versionCatalogParser.parseDependencyInfo()
        val checkGradleUpdate = currentGradleVersion != null
        val updatedVersionsMutex = Mutex()
        var updatedGradleVersion: String? = null
        val updatedVersions = mutableListOf<DependencyUpdateResult>()
        val nbDependencies = versionCatalog.dependencies.size + if (checkGradleUpdate) 1 else 0
        completed = 0
        progressFlow.value =
            DependencyUpdateChecker.Progress.Determinate(FINDING_UPDATES_TASK, 0)
        coroutineScope {
            versionCatalog
                .dependencies
                .asSequence()
                .map { (key, dep) ->
                    async {
                        if (!configuration.isExcluded(key, dep)) {
                            logger.info("Finding updated version for ${dep.moduleId}")
                            val currentVersion = dep.version?.resolve(versionCatalog.versions)
                            if (currentVersion == null || configuration.onlyCheckStaticVersions && !currentVersion.isStatic) {
                                return@async
                            }
                            val updatedVersion = findUpdatedVersion(
                                dependency = dep,
                                versionReferences = versionCatalog.versions
                            )
                            if (updatedVersion != null) {
                                logger.info("Found updated version ${updatedVersion.updatedVersion} for ${dep.moduleId}")
                                updatedVersionsMutex.withLock {
                                    updatedVersions.add(
                                        DependencyUpdateResult(
                                            dependencyKey = key,
                                            dependency = dep,
                                            repository = updatedVersion.repository,
                                            currentVersion = currentVersion,
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
                .plus(
                    async {
                        if (currentGradleVersion != null) {
                            logger.info("Finding updated Gradle version")
                            updatedGradleVersion = findGradleUpdatedVersion(currentGradleVersion)
                            if (updatedGradleVersion != null) {
                                logger.debug("Found updated Gradle version $updatedGradleVersion")
                            }
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
        val nbUpdatedVersions = updatedVersions.size
        completed = 0
        val updateInfosMutex = Mutex()
        val updatesInfos = mutableMapOf<UpdateInfo.Type, MutableList<UpdateInfo>>()
        coroutineScope {
            updatedVersions
                .map { result ->
                    async {
                        logger.info("Finding Maven info for ${result.dependency.moduleId}")
                        val (type, updateInfo) = computeUpdateInfo(result)
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
        // Sort and return
        return DependenciesUpdateResult(
            gradleUpdateInfo = if (updatedGradleVersion != null && currentGradleVersion != null) {
                GradleUpdateInfo(
                    currentVersion = currentGradleVersion,
                    updatedVersion = updatedGradleVersion!!
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

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun executeRepositoryRequest(
        repository: Repository,
        urlBuilder: URLBuilder.() -> Unit = {}
    ) = httpClient.get(repository.url) {
        url(urlBuilder)
        if (repository.user != null && repository.password != null) {
            header(
                HttpHeaders.Authorization,
                "Basic ${Base64.encode("${repository.user}:${repository.password}".encodeToByteArray())}"
            )
        }
    }

    private suspend fun findGradleUpdatedVersion(
        currentGradleVersion: String
    ): String? {
        try {
            val currentVersion = SemanticVersion.parse(currentGradleVersion, strict = false)
            val updatedVersionString = withContext(ioDispatcher) {
                httpClient
                    .get(configuration.gradleCurrentVersionUrl)
                    .takeIf { it.status.isSuccess() }
                    ?.body<GradleVersion>()
                    ?.version
            }
            val updatedVersion = updatedVersionString?.let { SemanticVersion.parse(it, strict = false) }
            return if (updatedVersion == null || updatedVersion <= currentVersion) {
                null
            } else {
                updatedVersionString
            }
        } catch (ignored: VersionFormatException) {
            return null
        }
    }

    private suspend fun findUpdatedVersion(
        dependency: Dependency,
        versionReferences: Map<String, Version.Resolved>
    ): VersionResult? {
        val repositories = when (dependency) {
            is Dependency.Library -> configuration.repositories
            is Dependency.Plugin -> configuration.pluginRepositories
        }
        return repositories
            .asFlow()
            .mapNotNull { repository ->
                findUpdatedVersion(
                    dependency = dependency,
                    versionReferences = versionReferences,
                    repository = repository
                )?.let { VersionResult(it, repository) }
            }
            .firstOrNull()
    }

    private suspend fun findUpdatedVersion(
        dependency: Dependency,
        versionReferences: Map<String, Version.Resolved>,
        repository: Repository
    ): GradleDependencyVersion.Static? {
        val version = dependency.version?.resolve(versionReferences) ?: return null
        val group = dependency.group ?: return null
        val name = dependency.name ?: return null
        val versioning = withContext(ioDispatcher) {
            executeRepositoryRequest(repository) {
                appendPathSegments(group.split('.'))
                appendPathSegments(name, "maven-metadata.xml")
            }
                .takeIf { it.status.isSuccess() }
                ?.body<Metadata>()
                ?.versioning
        } ?: return null
        return sequenceOf(versioning.release, versioning.latest)
            .plus(versioning.versions.asSequence().map { it.version })
            .filterNotNull()
            .filterIsInstance<GradleDependencyVersion.Static>()
            .filter { version.isUpdate(it) }
            .filterNot { policy?.select(version, it) == false }
            .maxOrNull()
    }

    private suspend fun computeUpdateInfo(result: DependencyUpdateResult): Pair<UpdateInfo.Type, UpdateInfo> {
        val mavenInfo = getMavenInfo(result.dependency, result.repository, result.updatedVersion)
        val type = when (result.dependency) {
            is Dependency.Library -> UpdateInfo.Type.LIBRARY
            is Dependency.Plugin -> UpdateInfo.Type.PLUGIN
        }
        return type to UpdateInfo(
            dependency = result.dependencyKey,
            dependencyId = result.dependency.moduleId,
            name = mavenInfo?.name,
            url = mavenInfo?.url,
            currentVersion = result.currentVersion.toString(),
            updatedVersion = result.updatedVersion.toString()
        )
    }

    private suspend fun getMavenInfo(
        dependency: Dependency,
        repository: Repository,
        updatedVersion: GradleDependencyVersion.Static
    ): MavenInfo? {
        val group = dependency.group ?: return null
        val name = dependency.name ?: return null
        val mavenInfo = withContext(ioDispatcher) {
            executeRepositoryRequest(repository) {
                appendPathSegments(group.split('.'))
                appendPathSegments(name, updatedVersion.toString(), "$name-$updatedVersion.pom")
            }.takeIf { it.status.isSuccess() }?.body<MavenInfo>()
        }
        // If this is a plugin, we need to find the real maven info by following the dependency
        return if (dependency is Dependency.Plugin && mavenInfo != null) {
            val realDependency = mavenInfo.dependencies.singleOrNull() ?: return mavenInfo
            val realVersion = realDependency
                .version
                ?.let { GradleDependencyVersion(it) }
                ?.takeUnless { it is GradleDependencyVersion.Unknown } as? GradleDependencyVersion.Static
                ?: return mavenInfo
            logger.debug("Resolving plugin dependency ${dependency.id} to ${realDependency.groupId}:${realDependency.artifactId}:$realVersion")
            getMavenInfo(
                dependency = Dependency.Library(
                    group = realDependency.groupId,
                    name = realDependency.artifactId,
                ),
                repository = repository,
                updatedVersion = realVersion
            )
        } else {
            mavenInfo
        }
    }

    private data class VersionResult(
        val updatedVersion: GradleDependencyVersion.Static,
        val repository: Repository
    ) : Comparable<VersionResult> {
        override fun compareTo(other: VersionResult): Int {
            return updatedVersion.compareTo(other.updatedVersion)
        }
    }

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
 * @param path The path to the version catalog.
 */
public class NoVersionCatalogException(path: Path) : Exception("No version catalog found at $path")