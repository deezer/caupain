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

import com.deezer.caupain.DependencyVersionsReplacer
import com.deezer.caupain.GradleWrapperVersionReplacer
import com.deezer.caupain.model.GradleUpdateInfo
import com.deezer.caupain.plugin.internal.DefaultJson
import com.deezer.caupain.plugin.internal.toOkioPath
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.wrapper.internal.GradleVersionResolver
import org.gradle.internal.extensions.core.get
import org.gradle.work.DisableCachingByDefault
import java.util.Properties

/**
 * Dependencies replacement task.
 */
@OptIn(ExperimentalSerializationApi::class)
@DisableCachingByDefault(because = "This task is used to replace dependencies in-place, so caching is not applicable.")
open class DependenciesReplaceTask : DefaultTask() {

    @get:[InputFile PathSensitive(PathSensitivity.RELATIVE)]
    val serializedUpdates: RegularFileProperty = project.objects.fileProperty()

    @get:OutputFile
    val versionCatalogFile: RegularFileProperty = project.objects.fileProperty()

    @get:OutputFile
    val wrapperArguments: RegularFileProperty = project.objects.fileProperty()

    init {
        group = "verification"
        description = "Replace dependencies in-place with their latest versions."
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun replaceDependencies() {
        val replacer = DependencyVersionsReplacer(
            gradleVersionReplacer = ArgumentsFiller(wrapperArguments),
        )
        val input = serializedUpdates
            .get()
            .asFile
            .inputStream()
            .use { DefaultJson.decodeFromStream<DependencyVersionsReplacer.Input>(it) }
        runBlocking {
            replacer.replaceVersions(
                versionCatalogPath = versionCatalogFile.get().toOkioPath(),
                input = input
            )
        }
    }

    private class ArgumentsFiller(private val wrapperArguments: RegularFileProperty) :
        GradleWrapperVersionReplacer {

        override suspend fun replaceGradleWrapperVersion(updateInfo: GradleUpdateInfo) {
            wrapperArguments.get().asFile.bufferedWriter().use { writer ->
                Properties().apply {
                    setProperty(CAN_UPDATE_WRAPPER, "true")
                    setProperty(GRADLE_UPDATE_VERSION, updateInfo.updatedVersion)
                    updateInfo.checksum?.let { setProperty(GRADLE_UPDATE_CHECKSUM, it) }
                    store(writer, null)
                }
            }
        }
    }

    companion object {
        internal const val CAN_UPDATE_WRAPPER = "update"
        internal const val GRADLE_UPDATE_VERSION = "version"
        internal const val GRADLE_UPDATE_CHECKSUM = "checksum"
    }
}
