package com.deezer.dependencies

import com.deezer.dependencies.DependencyUpdateChecker.ProgressListener
import com.deezer.dependencies.model.ALL_POLICIES
import com.deezer.dependencies.model.Configuration
import com.deezer.dependencies.model.Dependency
import com.deezer.dependencies.model.GradleDependencyVersion
import com.deezer.dependencies.model.Logger
import com.deezer.dependencies.model.Policy
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
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.xml.xml
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.SYSTEM
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// TODO : handle errors
public class DependencyUpdateChecker internal constructor(
    private val configuration: Configuration,
    private val httpClient: HttpClient,
    private val fileSystem: FileSystem,
    private val ioDispatcher: CoroutineDispatcher,
    private val versionCatalogParser: VersionCatalogParser,
    private val policies: Map<String, Policy>,
    private val logger: Logger,
    private val progressListener: ProgressListener,
) {
    public constructor(
        configuration: Configuration,
        logger: Logger = Logger.EMPTY,
        progressListener: ProgressListener = ProgressListener.EMPTY,
    ) : this(
        configuration = configuration,
        fileSystem = FileSystem.SYSTEM,
        httpClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                xml(DefaultXml)
            }
            install(Logging) {
                this.logger = logger
                level = LogLevel.ALL
            }
        },
        ioDispatcher = Dispatchers.IO,
        versionCatalogParser = DefaultVersionCatalogParser(
            toml = DefaultToml,
            versionCatalogPath = configuration.versionCatalogPath,
            fileSystem = FileSystem.SYSTEM,
            ioDispatcher = Dispatchers.IO
        ),
        policies = ALL_POLICIES,
        logger = logger,
        progressListener = progressListener
    )

    private val policy = configuration.policy?.let(policies::get)

    public suspend fun checkForUpdates(): List<UpdateInfo> {
        logger.info("Parsing version catalog")
        logger.debug("Version catalog path is ${fileSystem.canonicalize(configuration.versionCatalogPath)}")
        progressListener.onProgress(Progress.Indeterminate)
        val versionCatalog = versionCatalogParser.parseDependencyInfo()
        val updatedVersions = mutableListOf<DependencyUpdateResult>()
        val nbDependencies = versionCatalog.dependencies.size
        progressListener.onProgress(Progress.Determinate(0))
        versionCatalog
            .dependencies
            .asSequence()
            .forEachIndexed { index, (key, dep) ->
                if (!configuration.isExcluded(key, dep)) {
                    logger.debug("Finding updated version for ${dep.moduleId}")
                    val updatedVersion = findUpdatedVersion(
                        dependency = dep,
                        versionReferences = versionCatalog.versions
                    )
                    if (updatedVersion != null) {
                        logger.debug("Found updated version ${updatedVersion.updatedVersion} for ${dep.moduleId}")
                        updatedVersions.add(
                            DependencyUpdateResult(
                                dependencyKey = key,
                                dependency = dep,
                                repository = updatedVersion.repository,
                                updatedVersion = updatedVersion.updatedVersion
                            )
                        )
                    }
                }
                val percentage = ((index + 1) * 100) / (nbDependencies * 2)
                progressListener.onProgress(Progress.Determinate(percentage))
            }
        val nbUpdatedVersions = updatedVersions.size
        return updatedVersions
            .asIterable()
            .asFlow()
            .onEach { logger.debug("Finding Maven info for ${it.dependency.moduleId}") }
            .map { computeUpdateInfo(it) }
            .withIndex()
            .onEach { (index, _) ->
                val percentage = (((index + 1) * 100) / (nbUpdatedVersions / 2)) + 50
                progressListener.onProgress(Progress.Determinate(percentage))
            }
            .map { it.value }
            .toList()
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
            .filter { policy == null || policy.select(version, it) }
            .maxOrNull()
    }

    private suspend fun computeUpdateInfo(result: DependencyUpdateResult): UpdateInfo {
        val mavenInfo = getMavenInfo(result.dependency, result.repository, result.updatedVersion)
        return UpdateInfo(
            dependency = result.dependencyKey,
            dependencyId = result.dependency.moduleId,
            type = when (result.dependency) {
                is Dependency.Library -> UpdateInfo.Type.LIBRARY
                is Dependency.Plugin -> UpdateInfo.Type.PLUGIN
            },
            name = mavenInfo?.name,
            url = mavenInfo?.url,
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
            val realVersion =
                GradleDependencyVersion(realDependency.version) as? GradleDependencyVersion.Single
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
        val updatedVersion: GradleDependencyVersion.Single,
    )

    public sealed interface Progress {
        public data object Indeterminate : Progress

        public data class Determinate(val percentage: Int) : Progress
    }

    public fun interface ProgressListener {
        public fun onProgress(progress: Progress)

        public companion object {
            public val EMPTY: ProgressListener = ProgressListener { }
        }
    }
}