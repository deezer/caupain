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

package com.deezer.caupain.plugin.internal

import com.deezer.caupain.model.Dependency
import com.deezer.caupain.model.Dependency.Library
import com.deezer.caupain.model.Dependency.Plugin
import com.deezer.caupain.model.Repository
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.credentials.Credentials
import org.gradle.api.credentials.HttpHeaderCredentials
import org.gradle.api.internal.artifacts.repositories.ArtifactResolutionDetails
import org.gradle.api.internal.artifacts.repositories.ContentFilteringRepository
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.internal.artifacts.repositories.AuthenticationSupportedInternal
import org.gradle.kotlin.dsl.listProperty
import kotlin.jvm.optionals.getOrNull
import com.deezer.caupain.model.Credentials as ModelCredentials
import com.deezer.caupain.model.HeaderCredentials as ModelHeaderCredentials
import com.deezer.caupain.model.PasswordCredentials as ModelPasswordCredentials

private val ACCEPTED_SCHEMES = setOf("http", "https")

internal fun RepositoryHandler.toRepositories(objects: ObjectFactory): Provider<List<Repository>> {
    val repositoriesProvider = objects.listProperty<Repository>()
    for (repository in this) {
        if (repository is MavenArtifactRepository && repository.url.scheme in ACCEPTED_SCHEMES) {
            val credentials =
                (repository as? AuthenticationSupportedInternal)?.configuredCredentials
            if (credentials == null) {
                repositoriesProvider.add(RepositoryDelegate(repository))
            } else {
                repositoriesProvider.add(
                    credentials
                        .asOptional()
                        .map { optionalCredentials ->
                            RepositoryDelegate(
                                delegate = repository,
                                credentials = optionalCredentials.getOrNull()
                            )
                        }
                )
            }
        }
    }
    return repositoriesProvider
}

private class RepositoryDelegate(
    delegate: MavenArtifactRepository,
    credentials: Credentials? = null,
) : Repository {

    private val filterAction = (delegate as? ContentFilteringRepository)?.contentFilter

    override val url: String = delegate.url.toString()

    override val credentials = credentials?.toModelCredentials()

    override fun contains(dependency: Dependency): Boolean {
        val action = filterAction ?: return true
        if (dependency.group == null || dependency.name == null) return false
        return ArtifactResolutionDetailsAdapter(dependency)
            .apply { action.execute(this) }
            .isFound
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RepositoryDelegate

        if (filterAction != other.filterAction) return false
        if (url != other.url) return false
        if (credentials != other.credentials) return false

        return true
    }

    override fun hashCode(): Int {
        var result = filterAction?.hashCode() ?: 0
        result = 31 * result + url.hashCode()
        result = 31 * result + (credentials?.hashCode() ?: 0)
        return result
    }
}

private fun Credentials.toModelCredentials(): ModelCredentials? {
    return when (this) {
        is PasswordCredentials -> {
            val user = username ?: return null
            val password = password ?: return null
            ModelPasswordCredentials(user, password)
        }

        is HttpHeaderCredentials -> {
            val name = name ?: return null
            val value = value ?: return null
            ModelHeaderCredentials(name, value)
        }

        else -> null
    }
}

private class ModuleIdentifierAdapter(private val dependency: Dependency) : ModuleIdentifier {
    override fun getGroup(): String = requireNotNull(dependency.group)

    override fun getName(): String = requireNotNull(dependency.name)
}

private class ModuleComponentIdentifierAdapter(private val dependency: Dependency) :
    ModuleComponentIdentifier {
    private val identifier = ModuleIdentifierAdapter(dependency)

    override fun getDisplayName(): String = dependency.moduleId

    override fun getGroup(): String = identifier.group

    override fun getModule(): String = identifier.name

    override fun getVersion(): String = requireNotNull(dependency.version).toString()

    override fun getModuleIdentifier(): ModuleIdentifier = identifier
}

private class ArtifactResolutionDetailsAdapter(
    private val dependency: Dependency
) : ArtifactResolutionDetails {

    var isFound = true
        private set

    override fun getModuleId(): ModuleIdentifier = ModuleIdentifierAdapter(dependency)

    override fun getComponentId(): ModuleComponentIdentifier? =
        if (dependency.version == null) {
            null
        } else {
            ModuleComponentIdentifierAdapter(dependency)
        }

    override fun isVersionListing(): Boolean = dependency.version != null

    override fun notFound() {
        isFound = false
    }
}

private val Dependency.group: String?
    get() = when (this) {
        is Library -> group
        is Plugin -> id
    }

private val Dependency.name: String?
    get() = when (this) {
        is Library -> name
        is Plugin -> "$id.gradle.plugin"
    }