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
import com.deezer.caupain.model.versionCatalog.Version
import com.deezer.caupain.plugin.RepositoryInfo
import org.gradle.api.Action
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
import org.gradle.api.provider.ProviderFactory
import org.gradle.internal.artifacts.repositories.AuthenticationSupportedInternal
import java.util.Optional
import kotlin.jvm.optionals.getOrNull
import com.deezer.caupain.model.Credentials as ModelCredentials
import com.deezer.caupain.model.HeaderCredentials as ModelHeaderCredentials
import com.deezer.caupain.model.PasswordCredentials as ModelPasswordCredentials

private val ACCEPTED_SCHEMES = setOf("http", "https")

internal fun RepositoryHandler.toRepositories(
    objects: ObjectFactory,
    providers: ProviderFactory,
): Provider<List<RepositoryInfo>> {
    val repositoriesProvider = objects.listProperty<RepositoryInfo>()
    configureEach { repository ->
        if (repository is MavenArtifactRepository && repository.url.scheme in ACCEPTED_SCHEMES) {
            val credentialsInfoProvider = repository.getCredentialsInfoProvider(providers)
            val filterActionInfo = repository.getFilterActionInfo()

            repositoriesProvider.add(
                credentialsInfoProvider.map { optionalCredentialsInfo ->
                    val credentialsInfo = optionalCredentialsInfo.getOrNull()
                    RepositoryInfo(
                        repository = RepositoryDelegate(
                            filterAction = filterActionInfo?.action,
                            url = repository.url.toString(),
                            credentials = credentialsInfo?.credentials,
                        ),
                        isCompatibleWithConfigCache =
                            filterActionInfo?.isCompatibleWithConfigCache != false
                                    && credentialsInfo?.isCompatibleWithConfigCache != false,
                    )
                }
            )
        }
    }
    return repositoriesProvider
}

internal class RepositoryDelegate(
    private val filterAction: Action<in ArtifactResolutionDetails>?,
    override val url: String,
    credentials: Credentials? = null,
) : Repository {

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
        if (isVersionListing) null else ModuleComponentIdentifierAdapter(dependency)

    override fun isVersionListing(): Boolean =
        (dependency.version as? Version.Resolved)?.isStatic != true

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

private data class FilterActionInfo(
    val action: Action<in ArtifactResolutionDetails>,
    val isCompatibleWithConfigCache: Boolean,
)

private fun MavenArtifactRepository.getFilterActionInfo(): FilterActionInfo? {
    if (this !is ContentFilteringRepository) return null
    val action = contentFilter
    val isCompatibleWithConfigCache = if (action.javaClass.isAnonymousClass) {
        true
    } else {
        // Check if getting filter twice returns the same action, it means we're not dealing with a
        // new instance each time
        action === contentFilter
    }
    return FilterActionInfo(action, isCompatibleWithConfigCache)
}

private data class CredentialsInfo(
    val credentials: Credentials,
    val isCompatibleWithConfigCache: Boolean,
)

private fun MavenArtifactRepository.getCredentialsInfoProvider(
    providers: ProviderFactory
): Provider<Optional<CredentialsInfo>> {
    if (this !is AuthenticationSupportedInternal) return providers.provider { Optional.empty() }
    val credentialsProvider = configuredCredentials.asOptional()
    val configCacheCompatibilityProvider = isUsingCredentialsProvider.orElse(true)
    return credentialsProvider.flatMap { optionalCredentials ->
        if (optionalCredentials.isPresent) {
            configCacheCompatibilityProvider.map { isCompatibleWithConfigCache ->
                Optional.of(
                    CredentialsInfo(
                        credentials = optionalCredentials.get(),
                        isCompatibleWithConfigCache = isCompatibleWithConfigCache,
                    )
                )
            }
        } else {
            providers.provider { Optional.empty() }
        }
    }
}
