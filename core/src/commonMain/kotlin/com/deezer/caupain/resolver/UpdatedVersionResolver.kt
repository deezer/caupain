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

package com.deezer.caupain.resolver

import com.deezer.caupain.internal.HttpException
import com.deezer.caupain.internal.processRequest
import com.deezer.caupain.model.Dependency
import com.deezer.caupain.model.GradleDependencyVersion
import com.deezer.caupain.model.Logger
import com.deezer.caupain.model.Policy
import com.deezer.caupain.model.Repository
import com.deezer.caupain.model.executeRepositoryRequest
import com.deezer.caupain.model.group
import com.deezer.caupain.model.maven.Metadata
import com.deezer.caupain.model.maven.Versioning
import com.deezer.caupain.model.name
import com.deezer.caupain.model.versionCatalog.Version
import dev.drewhamilton.poko.Poko
import io.ktor.client.HttpClient
import io.ktor.http.appendPathSegments
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.transformWhile

/**
 * This checks Maven repositories for updated versions of a dependency.
 */
public interface UpdatedVersionResolver {

    /**
     * Query the repositories for an updated version of the given dependency.
     */
    public suspend fun getUpdatedVersion(
        dependency: Dependency,
        versionReferences: Map<String, Version.Resolved>,
    ): Result

    /**
     * Dependency update result.
     */
    public sealed interface Result {

        /**
         * Indicates that there was a failure during the update check
         */
        public data object Failure : Result

        /**
         * Indicates that there is no updated version
         */
        public data object UpToDate : Result

        /**
         * Indicates that an update has been found
         *
         * @property currentVersion The current version of the dependency.
         * @property updatedVersion The updated version of the dependency.
         * @property repository The repository where the updated version was found.
         */
        @Poko
        public class Updated(
            public val currentVersion: Version.Resolved,
            public val updatedVersion: GradleDependencyVersion.Static,
            public val repository: Repository
        ) : Result, Comparable<Updated> {

            override fun compareTo(other: Updated): Int {
                return updatedVersion.compareTo(other.updatedVersion)
            }
        }
    }
}

@Suppress("LongParameterList") // Needed for dependency injection
internal class DefaultUpdatedVersionResolver(
    httpClient: HttpClient,
    private val repositories: List<Repository>,
    private val pluginRepositories: List<Repository>,
    private val logger: Logger,
    private val onlyCheckStaticVersions: Boolean,
    policies: Iterable<Policy>,
    ioDispatcher: CoroutineDispatcher,
    verifyExistence: Boolean,
    mavenInfoResolver: MavenInfoResolver,
) : UpdatedVersionResolver {

    private val versionResolver = object : AbstractVersionResolver<DependencyRequestInfo>(
        httpClient = httpClient,
        ioDispatcher = ioDispatcher
    ) {
        override fun DependencyRequestInfo.isUpdatedVersion(
            version: GradleDependencyVersion.Static
        ): Boolean = dependency.version?.resolve(versionReferences)?.isUpdate(version) == true

        @Suppress("SuspendFunWithCoroutineScopeReceiver") // We need to use HttpClient as receiver
        override suspend fun HttpClient.getAvailableVersions(item: DependencyRequestInfo): Result<Sequence<GradleDependencyVersion>> {
            val group = item.dependency.group
            val name = item.dependency.name
            if (group == null || name == null) {
                return Result.failure(InvalidDependencyException(group, name))
            }
            return httpClient
                .processRequest<Metadata, Versioning>(transform = { it.versioning }) {
                    executeRepositoryRequest(item.repository) {
                        appendPathSegments(group.split('.'))
                        appendPathSegments(name, "maven-metadata.xml")
                    }
                }
                .map { versioning ->
                    sequenceOf(versioning.release, versioning.latest)
                        .plus(versioning.versions.asSequence().map { it.version })
                        .filterNotNull()
                }
                .onFailure { error ->
                    if (error !is HttpException) {
                        logger.error(
                            "Unable to fetch maven metadata for $group:$name from ${item.repository.url}",
                            error
                        )
                    }
                }
        }

        override suspend fun canSelectVersion(
            item: DependencyRequestInfo,
            version: GradleDependencyVersion.Static
        ): Boolean {
            val dependencyVersion = item.dependency.version?.resolve(item.versionReferences)
                ?: return false
            if (verifyExistence) {
                // Check that MavenInfo exists for this version (we only need the existence check, not the data).
                // If it doesn't exist (null), we can't proceed with this version.
                mavenInfoResolver.getMavenInfo(item.dependency, item.repository, version)
                    ?: return false
            }
            return policies.all { it.select(item.dependency, dependencyVersion, version) }
        }
    }

    override suspend fun getUpdatedVersion(
        dependency: Dependency,
        versionReferences: Map<String, Version.Resolved>,
    ): UpdatedVersionResolver.Result {
        logger.info("Finding updated version for ${dependency.moduleId}")
        val resolvedDependency = dependency.resolved(versionReferences)
        val currentVersion = resolvedDependency.version as? Version.Resolved
        if (currentVersion == null || onlyCheckStaticVersions && !currentVersion.isStatic) {
            return UpdatedVersionResolver.Result.UpToDate
        }
        val repositories = when (dependency) {
            is Dependency.Library -> repositories
            is Dependency.Plugin -> pluginRepositories
        }
        var result: UpdatedVersionResolver.Result = UpdatedVersionResolver.Result.Failure
        repositories
            .asFlow()
            .filter { resolvedDependency in it }
            .map { repository ->
                versionResolver
                    .findUpdatedVersion(
                        DependencyRequestInfo(
                            dependency = dependency,
                            versionReferences = versionReferences,
                            repository = repository
                        )
                    )
                    .map { updatedVersion ->
                        if (updatedVersion == null) {
                            UpdatedVersionResolver.Result.UpToDate
                        } else {
                            UpdatedVersionResolver.Result.Updated(
                                currentVersion = currentVersion,
                                updatedVersion = updatedVersion,
                                repository = repository,
                            )
                        }
                    }
            }
            .mapNotNull { it.getOrNull() }
            .transformWhile { currentResult ->
                emit(currentResult)
                currentResult !is UpdatedVersionResolver.Result.Updated
            }
            .collect { result = it }
        return result
    }

    private data class DependencyRequestInfo(
        val dependency: Dependency,
        val versionReferences: Map<String, Version.Resolved>,
        val repository: Repository,
    )
}

private class InvalidDependencyException(group: String?, name: String?) :
    Exception("Invalid dependency $group:$name")
