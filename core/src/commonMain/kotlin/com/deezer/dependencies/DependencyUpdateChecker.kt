package com.deezer.dependencies

import com.deezer.dependencies.DependencyUpdateChecker.Companion.FINDING_UPDATES_TASK
import com.deezer.dependencies.DependencyUpdateChecker.Companion.GATHERING_INFO_TASK
import com.deezer.dependencies.DependencyUpdateChecker.Companion.PARSING_TASK
import com.deezer.dependencies.internal.FileStorage
import com.deezer.dependencies.internal.extension
import com.deezer.dependencies.model.Configuration
import com.deezer.dependencies.model.DEFAULT_POLICIES
import com.deezer.dependencies.model.Dependency
import com.deezer.dependencies.model.GradleDependencyVersion
import com.deezer.dependencies.model.Logger
import com.deezer.dependencies.model.Policy
import com.deezer.dependencies.model.PolicyLoader
import com.deezer.dependencies.model.Repository
import com.deezer.dependencies.model.UpdateInfo
import com.deezer.dependencies.model.isExcluded
import com.deezer.dependencies.model.maven.MavenInfo
import com.deezer.dependencies.model.maven.Metadata
import com.deezer.dependencies.model.versionCatalog.Version
import com.deezer.dependencies.serialization.DefaultToml
import com.deezer.dependencies.serialization.DefaultXml
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
import io.ktor.serialization.kotlinx.xml.xml
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

public interface DependencyUpdateChecker {
    public val progress: Flow<Progress?>

    public suspend fun checkForUpdates(): Map<UpdateInfo.Type, List<UpdateInfo>>

    public sealed interface Progress {
        public val taskName: String

        public data class Indeterminate(override val taskName: String) : Progress

        public data class Determinate(
            override val taskName: String,
            val percentage: Int,
        ) : Progress
    }

    public companion object {
        internal const val PARSING_TASK = "Parsing version catalog"
        internal const val FINDING_UPDATES_TASK = "Finding updates"
        internal const val GATHERING_INFO_TASK = "Gathering update info"
        public val MAX_TASK_NAME_LENGTH: Int =
            listOf(PARSING_TASK, FINDING_UPDATES_TASK, GATHERING_INFO_TASK).maxOf { it.length }
    }
}

public class DefaultDependencyUpdateChecker internal constructor(
    private val configuration: Configuration,
    private val httpClient: HttpClient,
    private val fileSystem: FileSystem,
    private val ioDispatcher: CoroutineDispatcher,
    private val versionCatalogParser: VersionCatalogParser,
    private val logger: Logger,
    policies: Map<String, Policy>?,
) : DependencyUpdateChecker {
    public constructor(
        configuration: Configuration,
        logger: Logger = Logger.EMPTY,
        fileSystem: FileSystem = FileSystem.SYSTEM,
        policies: Map<String, Policy>? = null
    ) : this(
        configuration = configuration,
        fileSystem = fileSystem,
        httpClient = HttpClient {
            install(ContentNegotiation) {
                xml(DefaultXml, ContentType.Any)
            }
            install(Logging) {
                this.logger = logger
                level = LogLevel.HEADERS
            }
            install(HttpCache) {
                if (configuration.cacheDir != null) {
                    fileSystem.createDirectories(configuration.cacheDir)
                    publicStorage(FileStorage(fileSystem, configuration.cacheDir))
                }
            }
        },
        ioDispatcher = Dispatchers.IO,
        versionCatalogParser = DefaultVersionCatalogParser(
            toml = DefaultToml,
            versionCatalogPath = configuration.versionCatalogPath,
            fileSystem = fileSystem,
            ioDispatcher = Dispatchers.IO
        ),
        logger = logger,
        policies = policies
    )

    private val policies by lazy {
        policies ?: buildMap {
            putAll(DEFAULT_POLICIES)
            configuration
                .policyPluginDir
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

    private val progressFlow = MutableStateFlow<DependencyUpdateChecker.Progress?>(null)

    override val progress: Flow<DependencyUpdateChecker.Progress?>
        get() = progressFlow.asStateFlow()

    public override suspend fun checkForUpdates(): Map<UpdateInfo.Type, List<UpdateInfo>> {
        if (!fileSystem.exists(configuration.versionCatalogPath)) {
            throw NoVersionCatalogException(configuration.versionCatalogPath)
        }
        logger.debug("Version catalog path is ${fileSystem.canonicalize(configuration.versionCatalogPath)}")
        progressFlow.value = DependencyUpdateChecker.Progress.Indeterminate(PARSING_TASK)
        val versionCatalog = versionCatalogParser.parseDependencyInfo()
        val updatedVersionsMutex = Mutex()
        val updatedVersions = mutableListOf<DependencyUpdateResult>()
        val nbDependencies = versionCatalog.dependencies.size
        progressFlow.value =
            DependencyUpdateChecker.Progress.Determinate(FINDING_UPDATES_TASK, 0)
        coroutineScope {
            versionCatalog
                .dependencies
                .asSequence()
                .mapIndexed { index, (key, dep) ->
                    async {
                        if (!configuration.isExcluded(key, dep)) {
                            logger.debug("Finding updated version for ${dep.moduleId}")
                            val currentVersion = dep
                                .version
                                ?.resolve(versionCatalog.versions)
                                ?: return@async
                            val updatedVersion = findUpdatedVersion(
                                dependency = dep,
                                versionReferences = versionCatalog.versions
                            )
                            if (updatedVersion != null) {
                                logger.debug("Found updated version ${updatedVersion.updatedVersion} for ${dep.moduleId}")
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
                        val percentage = ((index + 1) * 100) / (nbDependencies * 2)
                        progressFlow.value = DependencyUpdateChecker.Progress.Determinate(
                            taskName = FINDING_UPDATES_TASK,
                            percentage = percentage
                        )
                    }
                }
                .toList()
                .awaitAll()
        }
        val nbUpdatedVersions = updatedVersions.size
        if (nbUpdatedVersions == 0) {
            progressFlow.value = DependencyUpdateChecker.Progress.Determinate(
                taskName = GATHERING_INFO_TASK,
                percentage = 100
            )
        }
        val updateInfosMutex = Mutex()
        val updatesInfos = mutableMapOf<UpdateInfo.Type, MutableList<UpdateInfo>>()
        coroutineScope {
            updatedVersions
                .asSequence()
                .withIndex()
                .map { (index, result) ->
                    async {
                        logger.debug("Finding Maven info for ${result.dependency.moduleId}")
                        val (type, updateInfo) = computeUpdateInfo(result)
                        updateInfosMutex.withLock {
                            val infosForType = updatesInfos[type]
                                ?: mutableListOf<UpdateInfo>().also { updatesInfos[type] = it }
                            infosForType.add(updateInfo)
                        }
                        val percentage = (((index + 1) * 100) / (nbUpdatedVersions * 2)) + 50
                        progressFlow.value = DependencyUpdateChecker.Progress.Determinate(
                            taskName = GATHERING_INFO_TASK,
                            percentage = percentage
                        )
                    }
                }
                .toList()
                .awaitAll()
            progressFlow.value = DependencyUpdateChecker.Progress.Determinate(
                taskName = GATHERING_INFO_TASK,
                percentage = 100
            )
        }
        // Sort
        return buildMap {
            for (type in UpdateInfo.Type.entries) {
                put(type, updatesInfos[type]?.sortedBy { it.dependencyId }.orEmpty())
            }
        }
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

    private suspend fun findUpdatedVersion(
        dependency: Dependency,
        versionReferences: Map<String, Version.Direct>
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
        versionReferences: Map<String, Version.Direct>,
        repository: Repository
    ): GradleDependencyVersion.Single? {
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
            .plus(versioning.versions)
            .filterNotNull()
            .filterIsInstance<GradleDependencyVersion.Single>()
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
        updatedVersion: GradleDependencyVersion.Single
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
                ?.takeUnless { it is GradleDependencyVersion.Unknown } as? GradleDependencyVersion.Single
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
        val updatedVersion: GradleDependencyVersion.Single,
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
        val currentVersion: Version.Direct,
        val updatedVersion: GradleDependencyVersion.Single,
    )
}

public class NoVersionCatalogException(path: Path) : Exception("No version catalog found at $path")