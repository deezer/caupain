package com.deezer.dependencies

import com.deezer.dependencies.model.Configuration
import com.deezer.dependencies.model.Dependency
import com.deezer.dependencies.model.GradleDependencyVersion
import com.deezer.dependencies.model.Repository
import com.deezer.dependencies.model.UpdateInfo
import com.deezer.dependencies.model.maven.MavenInfo
import com.deezer.dependencies.model.maven.Metadata
import com.deezer.dependencies.model.versionCatalog.Version
import com.deezer.dependencies.serialization.DefaultToml
import com.deezer.dependencies.serialization.DefaultXml
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
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
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.SYSTEM
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// TODO : add logging
public class DependencyUpdateChecker internal constructor(
    private val configuration: Configuration,
    private val httpClient: HttpClient,
    private val ioDispatcher: CoroutineDispatcher,
    private val versionCatalogParser: VersionCatalogParser
) {
    public constructor(configuration: Configuration) : this(
        configuration = configuration,
        httpClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                xml(DefaultXml)
            }
        },
        ioDispatcher = Dispatchers.IO,
        versionCatalogParser = DefaultVersionCatalogParser(
            toml = DefaultToml,
            versionCatalogPath = configuration.versionCatalogPath,
            fileSystem = FileSystem.SYSTEM,
            ioDispatcher = Dispatchers.IO
        )
    )

    public suspend fun checkForUpdates(): List<UpdateInfo> {
        val versionCatalog = versionCatalogParser.parseDependencyInfo()
        val updatedVersions = mutableListOf<DependencyUpdateResult>()
        for ((key, dep) in versionCatalog.dependencies) {
            if (key in configuration.excludedDependencies) continue
            val updatedVersion = findUpdatedVersion(
                dependency = dep,
                versionReferences = versionCatalog.versions
            )
            if (updatedVersion != null) {
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
        return updatedVersions
            .asIterable()
            .asFlow()
            .map { computeUpdateInfo(it) }
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
}