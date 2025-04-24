package com.deezer.caupain.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.support.zipTo
import java.io.File

open class MakeBinariesZipTask : DefaultTask() {

    @get:Internal
    val binDir = project.layout.buildDirectory.dir("bin")

    @get:OutputDirectory
    val zipDir = binDir.map { it.dir("zip") }

    @get:InputFiles
    val binFiles = project.files(
        Architectures.values().map { arch ->
            binDir.map { it.file(arch.filePath) }
        }
    )

    @get:Input
    val version = project.version.toString()

    @TaskAction
    fun copyAndZip() {
        val binDir = this.binDir.get().asFile
        val zipDir = this.zipDir.get().asFile
        zipDir.mkdirs()
        val outDir = File(binDir, "caupain")
        outDir.mkdirs()
        for (arch in Architectures.values()) {
            val binaryFile = File(binDir, arch.filePath)
            outDir.listFiles()?.forEach { it.delete() }
            val outFileName = buildString {
                append(binaryFile.nameWithoutExtension)
                if (arch.outExt != null) {
                    append('.')
                    append(arch.outExt)
                }
            }
            val outFile = File(outDir, outFileName)
            binaryFile.copyTo(outFile)
            val zipFile = File(zipDir, "caupain-$version-${arch.platformName}.zip")
            zipTo(zipFile, outDir)
        }
        outDir.deleteRecursively()
    }
}

enum class Architectures(
    val platformName: String,
    private val archName: String,
    private val ext: String,
    val outExt: String?
) {
    MACOS_ARM("macos-silicon", "macosArm64", "kexe", null),
    MACOS_X86("macos-intel", "macosX64", "kexe", null),
    LINUX("linux", "linuxX64", "kexe", null),
    WINDOWS("windows", "mingwX64", "exe", "exe");

    val filePath: String
        get() = "${archName}/releaseExecutable/caupain.${ext}"
}