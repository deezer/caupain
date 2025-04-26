package com.deezer.caupain.resolver

import com.deezer.caupain.model.Dependency
import com.deezer.caupain.model.GradleDependencyVersion
import com.deezer.caupain.model.Logger
import com.deezer.caupain.model.Policy
import com.deezer.caupain.model.Repository
import com.deezer.caupain.model.executeRepositoryRequest
import com.deezer.caupain.model.maven.Metadata
import com.deezer.caupain.model.versionCatalog.Version
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.http.appendPathSegments
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext

internal class UpdatedVersionResolver(
    private val httpClient: HttpClient,
    private val repositories: List<Repository>,
    private val pluginRepositories: List<Repository>,
    private val logger: Logger,
    private val onlyCheckStaticVersions: Boolean,
    private val policy: Policy?,
    private val ioDispatcher: CoroutineDispatcher
) {
    suspend fun getUpdatedVersion(
        dependency: Dependency,
        versionReferences: Map<String, Version.Resolved>,
    ): Result? {
        logger.info("Finding updated version for ${dependency.moduleId}")
        val currentVersion = dependency.version?.resolve(versionReferences)
        if (currentVersion == null || onlyCheckStaticVersions && !currentVersion.isStatic) {
            return null
        }
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
                )?.let { Result(currentVersion, it, repository) }
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
            httpClient.executeRepositoryRequest(repository) {
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

    data class Result(
        val currentVersion: Version.Resolved,
        val updatedVersion: GradleDependencyVersion.Static,
        val repository: Repository
    ) : Comparable<Result> {
        override fun compareTo(other: Result): Int {
            return updatedVersion.compareTo(other.updatedVersion)
        }
    }
}