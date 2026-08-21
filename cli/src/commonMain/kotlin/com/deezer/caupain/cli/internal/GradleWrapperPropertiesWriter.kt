/*
 * MIT License
 *
 * Copyright (c) 2026 Deezer
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
 *
 */

package com.deezer.caupain.cli.internal

import com.deezer.caupain.GradleWrapperVersionReplacer
import com.deezer.caupain.cli.model.GRADLE_DISTRIBUTION_REGEX
import com.deezer.caupain.cli.model.GradleWrapperProperties
import com.deezer.caupain.cli.serialization.decodeFromProperties
import com.deezer.caupain.cli.serialization.encodeToProperties
import com.deezer.caupain.model.GradleUpdateInfo
import com.deezer.caupain.model.gradle.GradleVersion
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path

internal class GradleWrapperPropertiesWriter(
    private val fileSystem: FileSystem,
    private val ioDispatcher: CoroutineDispatcher,
    internal val gradleWrapperPropertiesPath: Path,
) : GradleWrapperVersionReplacer {

    override suspend fun replaceGradleWrapperVersion(updateInfo: GradleUpdateInfo) {
        withContext(ioDispatcher) {
            val originalProperties = decodeFromProperties<GradleWrapperProperties>(
                fileSystem = fileSystem,
                path = gradleWrapperPropertiesPath,
            )
            encodeToProperties(
                fileSystem = fileSystem,
                path = gradleWrapperPropertiesPath,
                value = originalProperties.copy(
                    distributionUrl = originalProperties
                        .distributionUrl
                        ?.replaceDistributionUrl(
                            currentVersion = updateInfo.currentVersion,
                            updatedVersion = updateInfo.updatedVersion
                        ),
                    distributionSha256Sum = updateInfo.checksum
                )
            )
        }
    }

    private fun Url.replaceDistributionUrl(
        currentVersion: GradleVersion,
        updatedVersion: GradleVersion
    ): Url {
        val builder = URLBuilder(this)
        val updatedPathSegments = builder.pathSegments.toMutableList()
        val lastPathSegment = updatedPathSegments.lastOrNull()
        if (lastPathSegment != null) {
            var didReplace = false
            val updatedLastPathSegment =
                GRADLE_DISTRIBUTION_REGEX.replace(lastPathSegment) { matchResult ->
                    val versionGroup = matchResult.groups[1]
                    if (versionGroup != null && versionGroup.value == currentVersion.version) {
                        didReplace = true
                        matchResult.replaceGroup(1, updatedVersion.version)
                    } else {
                        matchResult.value
                    }
                }
            if (didReplace) {
                updatedPathSegments[updatedPathSegments.lastIndex] = updatedLastPathSegment
                val penultimateIndex = updatedPathSegments.lastIndex - 1
                val repositoryPathSegment = updatedPathSegments.getOrNull(penultimateIndex)
                if (repositoryPathSegment == currentVersion.repositoryPathSegment) {
                    updatedPathSegments[penultimateIndex] = updatedVersion.repositoryPathSegment
                }
                builder.pathSegments = updatedPathSegments
                return builder.build()
            }
        }
        return Url(toString().replace(currentVersion.version, updatedVersion.version))
    }
}

private val GradleVersion.repositoryPathSegment: String
    get() = if (isSnapshot) "distributions-snapshots" else "distributions"
