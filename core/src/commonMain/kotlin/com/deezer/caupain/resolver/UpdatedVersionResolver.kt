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

import com.deezer.caupain.model.Dependency
import com.deezer.caupain.model.GradleDependencyVersion
import com.deezer.caupain.model.Logger
import com.deezer.caupain.model.Policy
import com.deezer.caupain.model.Repository
import com.deezer.caupain.model.executeRepositoryRequest
import com.deezer.caupain.model.group
import com.deezer.caupain.model.maven.Metadata
import com.deezer.caupain.model.name
import com.deezer.caupain.model.versionCatalog.Version
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.http.appendPathSegments
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext

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
    ): Result?

    /**
     * Dependency update result.
     *
     * @property currentVersion The current version of the dependency.
     * @property updatedVersion The updated version of the dependency.
     * @property repository The repository where the updated version was found.
     */
    public class Result(
        public val currentVersion: Version.Resolved,
        public val updatedVersion: GradleDependencyVersion.Static,
        public val repository: Repository
    ) : Comparable<Result> {
        override fun compareTo(other: Result): Int {
            return updatedVersion.compareTo(other.updatedVersion)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as Result

            if (currentVersion != other.currentVersion) return false
            if (updatedVersion != other.updatedVersion) return false
            if (repository != other.repository) return false

            return true
        }

        override fun hashCode(): Int {
            var result = currentVersion.hashCode()
            result = 31 * result + updatedVersion.hashCode()
            result = 31 * result + repository.hashCode()
            return result
        }

        override fun toString(): String {
            return "Result(currentVersion=$currentVersion, updatedVersion=$updatedVersion, repository=$repository)"
        }
    }
}

internal class DefaultUpdatedVersionResolver(
    private val httpClient: HttpClient,
    private val repositories: List<Repository>,
    private val pluginRepositories: List<Repository>,
    private val logger: Logger,
    private val onlyCheckStaticVersions: Boolean,
    private val policy: Policy?,
    private val ioDispatcher: CoroutineDispatcher
) : UpdatedVersionResolver {
    override suspend fun getUpdatedVersion(
        dependency: Dependency,
        versionReferences: Map<String, Version.Resolved>,
    ): UpdatedVersionResolver.Result? {
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
            .filter { dependency in it }
            .mapNotNull { repository ->
                findUpdatedVersion(
                    dependency = dependency,
                    versionReferences = versionReferences,
                    repository = repository
                )?.let { UpdatedVersionResolver.Result(currentVersion, it, repository) }
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
            httpClient
                .executeRepositoryRequest(repository) {
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
            .filterNot { policy?.select(dependency, version, it) == false }
            .maxOrNull()
    }
}