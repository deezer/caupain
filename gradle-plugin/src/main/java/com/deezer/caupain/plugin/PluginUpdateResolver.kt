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

package com.deezer.caupain.plugin

import com.deezer.caupain.DependencyUpdateChecker
import com.deezer.caupain.gradle_plugin.BuildConfig
import com.deezer.caupain.model.Dependency
import com.deezer.caupain.model.GradleDependencyVersion
import com.deezer.caupain.model.SelfUpdateInfo
import com.deezer.caupain.model.versionCatalog.Version
import com.deezer.caupain.model.versionCatalog.VersionCatalog
import com.deezer.caupain.resolver.SelfUpdateResolver

internal object PluginUpdateResolver : SelfUpdateResolver {

    override suspend fun resolveSelfUpdate(
        checker: DependencyUpdateChecker,
        versionCatalogs: List<VersionCatalog>,
    ): SelfUpdateInfo? {
        // Check if the plugin is already present in the version catalog
        if (versionCatalogs.any { it.containsCaupainPlugin() }) return null
        val currentVersion = Version.Simple(GradleDependencyVersion(BuildConfig.VERSION))
        val updatedVersion = checker.versionResolver.getUpdatedVersion(
            dependency = Dependency.Plugin(
                id = "com.deezer.caupain",
                version = currentVersion
            ),
            versionReferences = emptyMap()
        )
        return updatedVersion?.let { versionResult ->
            SelfUpdateInfo(
                currentVersion = currentVersion.toString(),
                updatedVersion = versionResult.updatedVersion.toString(),
                sources = listOf(SelfUpdateInfo.Source.PLUGINS)
            )
        }
    }
}

private fun VersionCatalog.containsCaupainPlugin(): Boolean {
    return plugins.any { it.value.id == "com.deezer.caupain" }
}