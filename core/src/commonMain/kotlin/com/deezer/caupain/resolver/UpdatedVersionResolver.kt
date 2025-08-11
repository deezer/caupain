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
import dev.drewhamilton.poko.Poko
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.http.appendPathSegments
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull

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
    @Poko
    public class Result(
        public val currentVersion: Version.Resolved,
        public val updatedVersion: GradleDependencyVersion.Static,
        public val repository: Repository
    ) : Comparable<Result> {
        override fun compareTo(other: Result): Int {
            return updatedVersion.compareTo(other.updatedVersion)
        }
    }
}

internal class DefaultUpdatedVersionResolver(
    httpClient: HttpClient,
    private val repositories: List<Repository>,
    private val pluginRepositories: List<Repository>,
    private val logger: Logger,
    private val onlyCheckStaticVersions: Boolean,
    private val policy: Policy?,
    ioDispatcher: CoroutineDispatcher
) : UpdatedVersionResolver {

    private val versionResolver = object : AbstractVersionResolver<DependencyRequestInfo>(
        httpClient = httpClient,
        ioDispatcher = ioDispatcher
    ) {
        override fun DependencyRequestInfo.isUpdatedVersion(
            version: GradleDependencyVersion.Static
        ): Boolean = dependency.version?.resolve(versionReferences)?.isUpdate(version) == true

        override suspend fun HttpClient.getAvailableVersions(item: DependencyRequestInfo): Sequence<GradleDependencyVersion> {
            val group = item.dependency.group ?: return emptySequence()
            val name = item.dependency.name ?: return emptySequence()
            val versioning = executeRepositoryRequest(item.repository) {
                appendPathSegments(group.split('.'))
                appendPathSegments(name, "maven-metadata.xml")
            }
                .takeIf { it.status.isSuccess() }
                ?.body<Metadata>()
                ?.versioning
                ?: return emptySequence()
            return sequenceOf(versioning.release, versioning.latest)
                .plus(versioning.versions.asSequence().map { it.version })
                .filterNotNull()
        }

        override fun canSelectVersion(
            item: DependencyRequestInfo,
            version: GradleDependencyVersion.Static
        ): Boolean {
            val policy = this@DefaultUpdatedVersionResolver.policy ?: return true
            val dependencyVersion = item.dependency.version?.resolve(item.versionReferences)
                ?: return false
            return policy.select(item.dependency, dependencyVersion, version)
        }
    }

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
                versionResolver.findUpdatedVersion(
                    DependencyRequestInfo(
                        dependency = dependency,
                        versionReferences = versionReferences,
                        repository = repository
                    )
                )?.let { UpdatedVersionResolver.Result(currentVersion, it, repository) }
            }
            .firstOrNull()
    }

    private data class DependencyRequestInfo(
        val dependency: Dependency,
        val versionReferences: Map<String, Version.Resolved>,
        val repository: Repository,
    )
}