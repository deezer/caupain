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

package com.deezer.caupain.tasks

import org.apache.commons.text.StringEscapeUtils
import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.property
import org.intellij.lang.annotations.Language
import java.io.File

abstract class CreateChocolateyFilesTask : DefaultTask() {

    @get:Input
    abstract val id: Property<String>

    @get:Input
    abstract val title: Property<String>

    @get:Input
    abstract val authors: Property<String>

    @get:Input
    val version = project.version

    @get:Input
    abstract val repositoryUrl: Property<String>

    @get:Input
    abstract val licenseUrl: Property<String>

    @get:Input
    abstract val tags: ListProperty<String>

    @get:Input
    abstract val summary: Property<String>

    @get:InputFile
    val readmeFile = project
        .objects
        .fileProperty()
        .convention(project.layout.projectDirectory.file("README.md"))

    @get:InputFile
    val licenceFile = project
        .objects
        .fileProperty()
        .convention(project.rootProject.layout.projectDirectory.file("LICENSE"))

    @get:OutputDirectory
    val outputDir = project
        .objects
        .directoryProperty()
        .convention(project.layout.buildDirectory.dir("distributions/chocolatey"))

    @TaskAction
    fun createFile() {
        val outputDir = this.outputDir.get().asFile.apply { mkdirs() }
        createSpec(File(outputDir, "caupain.nuspec"))
        val toolsDir = File(outputDir, "tools").apply { mkdirs() }
        createLicense(File(toolsDir, "LICENSE.txt"))
        File(toolsDir, "VERIFICATION.txt").writeText(verificationFile(repositoryUrl.get()))
    }

    private fun createSpec(specFile: File) {
        specFile.writeText(
            createSpec(
                id = id.get(),
                title = title.get(),
                authors = authors.get(),
                tags = tags.get().joinToString(" "),
                version = version.toString(),
                repositoryUrl = repositoryUrl.get(),
                licenceUrl = licenseUrl.get(),
                summary = summary.get(),
                description = buildString {
                    appendLine()
                    readmeFile.get().asFile.useLines { lines ->
                        lines
                            .dropWhile { !it.startsWith("##") }
                            .forEach { appendLine(StringEscapeUtils.escapeXml10(it)) }
                    }
                    appendLine()
                }
            )
        )
    }

    private fun createLicense(chocoLicenseFile: File) {
        chocoLicenseFile.bufferedWriter().use { writer ->
            writer.append("From: ")
            writer.appendLine(licenseUrl.get())
            writer.appendLine()
            writer.appendLine("LICENSE")
            writer.appendLine()
            licenceFile.get().asFile.bufferedReader().copyTo(writer)
            writer.appendLine()
        }
    }

    companion object {
        @Language("XML")
        private fun createSpec(
            id: String,
            title: String,
            authors: String,
            version: String,
            repositoryUrl: String,
            licenceUrl: String,
            description: String,
            tags: String,
            summary: String,
        ) = """<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://schemas.microsoft.com/packaging/2015/06/nuspec.xsd">
    <metadata>
        <id>$id</id>
        <version>$version</version>
        <packageSourceUrl>$repositoryUrl</packageSourceUrl>
        <authors>$authors</authors>
        <title>$title</title>
        <projectUrl>$repositoryUrl</projectUrl>
        <licenseUrl>$licenceUrl</licenseUrl>
        <requireLicenseAcceptance>false</requireLicenseAcceptance>
        <projectSourceUrl>$repositoryUrl</projectSourceUrl>
        <bugTrackerUrl>${"$repositoryUrl/issues"}</bugTrackerUrl>
        <tags>$tags</tags>
        <summary>$summary</summary>
        <description>$description</description>
    </metadata>
    <files>
        <file src="tools\**" target="tools" />
    </files>
</package>"""

        private fun verificationFile(repositoryUrl: String) = """
        VERIFICATION
        Verification is intended to assist the Chocolatey moderators and community
        in verifying that this package's contents are trustworthy.

        Releases can be found at $repositoryUrl/releases    
        """.trimIndent()
    }
}