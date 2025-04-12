package com.deezer.dependencies

import com.deezer.dependencies.model.DefaultRepositories
import com.deezer.dependencies.model.Dependency
import com.deezer.dependencies.model.GradleDependencyVersion
import com.deezer.dependencies.model.Repository
import com.deezer.dependencies.model.UpdateInfo
import com.deezer.dependencies.model.getVersion
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
import net.peanuuutz.tomlkt.Toml
import nl.adaptivity.xmlutil.serialization.XML
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// TODO : configuration
class DependencyUpdateChecker(
    private val xml: XML = DefaultXml,
    toml: Toml = DefaultToml,
    private val httpClient: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            xml(xml)
        }
    },
    versionCatalogPath: Path,
    fileSystem: FileSystem = FileSystem.SYSTEM,
    private val repositories: List<Repository> = listOf(
        DefaultRepositories.mavenCentral,
        DefaultRepositories.google,
    ),
    private val pluginRepositories: List<Repository> = listOf(
        DefaultRepositories.gradlePlugins,
        DefaultRepositories.mavenCentral,
        DefaultRepositories.google,
    ),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val versionCatalogParser = VersionCatalogParser(
        toml = toml,
        versionCatalogPath = versionCatalogPath,
        fileSystem = fileSystem,
        ioDispatcher = ioDispatcher
    )

    suspend fun checkForUpdates(): List<UpdateInfo> {
        val (versionCatalog, ignoredDependencyKeys) = versionCatalogParser.parseDependencyInfo()
        val updatedVersions = mutableMapOf<Dependency, VersionResult>()
        for ((key, dep) in versionCatalog.dependencies) {
            if (key in ignoredDependencyKeys) continue
            val updatedVersion = findUpdatedVersion(
                dependency = dep,
                versionReferences = versionCatalog.versions
            )
            if (updatedVersion != null) {
                updatedVersions[dep] = updatedVersion
            }
        }
        return updatedVersions
            .asIterable()
            .asFlow()
            .map { (dependency, versionResult) ->
                computeUpdateInfo(
                    dependency = dependency,
                    repository = versionResult.repository,
                    updatedVersion = versionResult.updatedVersion
                )
            }
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
                "Authorization",
                "Basic ${Base64.encode("${repository.user}:${repository.password}".encodeToByteArray())}"
            )
        }
    }

    private suspend fun findUpdatedVersion(
        dependency: Dependency,
        versionReferences: Map<String, Version>
    ): VersionResult? {
        val repositories = when (dependency) {
            is Dependency.Library -> repositories
            is Dependency.Plugin -> pluginRepositories
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
        versionReferences: Map<String, Version>,
        repository: Repository
    ): GradleDependencyVersion.Single? {
        val version = dependency.getVersion(versionReferences) ?: return null
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

    private suspend fun computeUpdateInfo(
        dependency: Dependency,
        repository: Repository,
        updatedVersion: GradleDependencyVersion.Single
    ): UpdateInfo {
        val mavenInfo = getMavenInfo(dependency, repository, updatedVersion)
        return UpdateInfo(
            dependency = dependency,
            name = mavenInfo?.name,
            url = mavenInfo?.url,
            updatedVersion = updatedVersion
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
}