package com.deezer.caupain.tasks

import org.apache.commons.text.StringEscapeUtils
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.property
import org.intellij.lang.annotations.Language
import java.io.File

open class CreateChocolateyFilesTask : DefaultTask() {

    @get:Input
    val version = project.version

    @get:Input
    val repositoryUrl = project.objects.property<String>()

    @get:Input
    val licenseUrl = project.objects.property<String>()

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
        File(toolsDir, "VERIFICATION.txt").writeText(VERIFICATION_FILE)
    }

    private fun createSpec(specFile: File) {
        specFile.writeText(
            createSpec(
                version = version.toString(),
                repositoryUrl = repositoryUrl.get(),
                licenceUrl = licenseUrl.get(),
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
            version: String,
            repositoryUrl: String,
            licenceUrl: String,
            description: String,
        ) = """<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://schemas.microsoft.com/packaging/2015/06/nuspec.xsd">
    <metadata>
        <id>caupain</id>
        <version>$version</version>
        <packageSourceUrl>$repositoryUrl</packageSourceUrl>
        <owners>Valentin Rocher</owners>
        <title>caupain</title>
        <authors>Valentin Rocher</authors>
        <projectUrl>$repositoryUrl</projectUrl>
        <licenseUrl>$licenceUrl</licenseUrl>
        <requireLicenseAcceptance>true</requireLicenseAcceptance>
        <projectSourceUrl>$repositoryUrl</projectSourceUrl>
        <bugTrackerUrl>${"$repositoryUrl/issues"}</bugTrackerUrl>
        <tags>gradle dependencies</tags>
        <summary>CLI tool to manage Gradle version catalog updates</summary>
        <description>$description</description>
    </metadata>
    <files>
        <file src="tools\**" target="tools" />
    </files>
</package>"""

        private val VERIFICATION_FILE = """
        VERIFICATION
        Verification is intended to assist the Chocolatey moderators and community
        in verifying that this package's contents are trustworthy.

        Releases can be found at https://github.com/bishiboosh/caupain/releases    
        """.trimIndent()
    }
}