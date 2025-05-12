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

package com.deezer.caupain.cli.model

import com.deezer.caupain.model.buildComponentFilter
import kotlinx.serialization.Serializable
import com.deezer.caupain.model.Repository as ModelRepository

sealed interface Repository {
    fun toModel(): ModelRepository

    data class Default(val repository: DefaultRepository) : Repository {
        override fun toModel(): ModelRepository = repository.repository
    }

    @Serializable
    data class Rich(
        val url: String,
        val user: String? = null,
        val password: String? = null,
        val includes: List<PackageSpec>? = null,
        val excludes: List<PackageSpec>? = null
    ) : Repository {
        override fun toModel(): ModelRepository {
            return ModelRepository(
                url = url,
                user = user,
                password = password,
                componentFilter = if (includes == null && excludes == null) {
                    null
                } else {
                    buildComponentFilter {
                        for (include in includes.orEmpty()) include(include.group, include.name)
                        for (exclude in excludes.orEmpty()) exclude(exclude.group, exclude.name)
                    }
                }
            )
        }
    }
}

@Serializable
data class PackageSpec(val group: String, val name: String? = null)

enum class DefaultRepository(val key: String, val repository: ModelRepository) {
    GOOGLE("google", com.deezer.caupain.model.DefaultRepositories.google),
    MAVEN_CENTRAL("mavenCentral", com.deezer.caupain.model.DefaultRepositories.mavenCentral),
    GRADLE_PLUGINS(
        "gradlePluginPortal",
        com.deezer.caupain.model.DefaultRepositories.gradlePlugins
    ),
}