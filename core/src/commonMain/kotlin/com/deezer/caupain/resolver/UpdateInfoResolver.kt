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
import com.deezer.caupain.model.Repository
import com.deezer.caupain.model.UpdateInfo
import com.deezer.caupain.model.executeRepositoryRequest
import com.deezer.caupain.model.group
import com.deezer.caupain.model.maven.MavenInfo
import com.deezer.caupain.model.maven.Metadata
import com.deezer.caupain.model.name
import com.deezer.caupain.model.versionCatalog.Version
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.http.appendPathSegments
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class UpdateInfoResolver(
    private val httpClient: HttpClient,
    private val ioDispatcher: CoroutineDispatcher,
    private val logger: Logger
) {
    suspend fun getUpdateInfo(
        key: String,
        dependency: Dependency,
        repository: Repository,
        currentVersion: Version.Resolved,
        updatedVersion: GradleDependencyVersion.Static
    ): Result {
        val mavenInfo = getMavenInfo(dependency, repository, updatedVersion)
        val type = when (dependency) {
            is Dependency.Library -> UpdateInfo.Type.LIBRARY
            is Dependency.Plugin -> UpdateInfo.Type.PLUGIN
        }
        return Result(
            type = type,
            info = UpdateInfo(
                dependency = key,
                dependencyId = dependency.moduleId,
                name = mavenInfo?.name,
                url = mavenInfo?.url,
                currentVersion = currentVersion.toString(),
                updatedVersion = updatedVersion.toString()
            )
        )
    }

    private suspend fun getMavenInfo(
        dependency: Dependency,
        repository: Repository,
        updatedVersion: GradleDependencyVersion.Static
    ): MavenInfo? {
        val resolvedUpdatedVersion = if (updatedVersion is GradleDependencyVersion.Snapshot) {
            resolveSnapshotVersion(dependency, repository, updatedVersion)
        } else {
            updatedVersion
        } ?: return null
        val group = dependency.group ?: return null
        val name = dependency.name ?: return null
        val mavenInfo = withContext(ioDispatcher) {
            httpClient.executeRepositoryRequest(repository) {
                appendPathSegments(group.split('.'))
                appendPathSegments(name, updatedVersion.toString(), "$name-$resolvedUpdatedVersion.pom")
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

    private suspend fun resolveSnapshotVersion(
        dependency: Dependency,
        repository: Repository,
        updatedVersion: GradleDependencyVersion.Snapshot
    ): GradleDependencyVersion.Static? {
        val group = dependency.group ?: return null
        val name = dependency.name ?: return null
        val snapshotMetadata = withContext(ioDispatcher) {
            httpClient.executeRepositoryRequest(repository) {
                appendPathSegments(group.split('.'))
                appendPathSegments(name, updatedVersion.toString(), "maven-metadata.xml")
            }
        }.takeIf { it.status.isSuccess() }?.body<Metadata>()
        return snapshotMetadata
            ?.versioning
            ?.snapshotVersions
            ?.firstOrNull { it.extension == "pom" }
            ?.value as? GradleDependencyVersion.Static
    }

    data class Result(
        val type: UpdateInfo.Type,
        val info: UpdateInfo
    )
}