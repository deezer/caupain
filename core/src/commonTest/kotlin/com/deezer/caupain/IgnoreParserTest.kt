package com.deezer.caupain

import com.deezer.caupain.model.Ignores
import com.deezer.caupain.toml.IgnoreParser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.intellij.lang.annotations.Language
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class IgnoreParserTest {

    private lateinit var fileSystem: FakeFileSystem

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setup() {
        fileSystem = FakeFileSystem()
    }

    @AfterTest
    fun teardown() {
        fileSystem.checkNoOpenFiles()
        fileSystem.close()
    }

    @Test
    fun testParser() {
        val path = "libs.versions.toml".toPath()
        fileSystem.write(path) { writeUtf8(FILE) }
        runTest(testDispatcher) {
            assertEquals(
                expected = Ignores(
                    refs = setOf("kotlin"),
                    libraryKeys = setOf("kotlin-test"),
                    pluginKeys = setOf("jetbrains-kotlin-jvm")
                ),
                actual = IgnoreParser(fileSystem, path, testDispatcher).computeIgnores()
            )
        }
    }
}

@Language("toml")
private val FILE = """
[versions]
junit = "4.13.2"
kotlin = "2.1.20" #ignoreUpdates
kotlinx-coroutines = "1.10.2"

[libraries]
kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin" } #ignoreUpdates
kotlin-test-junit = { module = "org.jetbrains.kotlin:kotlin-test-junit", version.ref = "kotlin" }

[bundles]
clikt = ["clikt", "clikt-markdown", "mordant", "mordant-coroutines"]
ktor = ["ktor-client-core", "ktor-client-logging", "ktor-client-content-negociation"]

[plugins]
kotlinMultiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlinx-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
jetbrains-kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "jetbrains-kotlin-jvm" } #ignoreUpdates
kotlinx-atomicfu = "org.jetbrains.kotlinx.atomicfu:0.27.0" #wrongcomment
dokka = "org.jetbrains.dokka:2.0.0"
detekt = { id = "io.gitlab.arturbosch.detekt", version.ref = "detekt" }
""".trimIndent()