package com.deezer.caupain.resolver

import com.deezer.caupain.model.GradleVersion
import io.github.z4kn4fein.semver.Version
import io.github.z4kn4fein.semver.VersionFormatException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class GradleVersionResolver(
    private val httpClient: HttpClient,
    private val gradleCurrentVersionUrl: String,
    private val ioDispatcher: CoroutineDispatcher
) {
    suspend fun getUpdatedVersion(currentGradleVersion: String): String? {
        return try {
            val currentVersion = Version.parse(currentGradleVersion, strict = false)
            val updatedVersionString = withContext(ioDispatcher) {
                httpClient
                    .get(gradleCurrentVersionUrl)
                    .takeIf { it.status.isSuccess() }
                    ?.body<GradleVersion>()
                    ?.version
            }
            val updatedVersion = updatedVersionString?.let { Version.parse(it, strict = false) }
            return if (updatedVersion == null || updatedVersion <= currentVersion) {
                null
            } else {
                updatedVersionString
            }
        } catch (ignored: VersionFormatException) {
            null
        }
    }
}