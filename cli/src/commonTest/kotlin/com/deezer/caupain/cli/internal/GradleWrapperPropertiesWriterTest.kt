/*
 * MIT License
 *
 * Copyright (c) 2026 Deezer
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
 *
 */

package com.deezer.caupain.cli.internal

import com.deezer.caupain.model.GradleUpdateInfo
import com.deezer.caupain.model.gradle.GradleVersion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class GradleWrapperPropertiesWriterTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var fileSystem: FakeFileSystem

    private val propsPath = "gradle-wrapper.properties".toPath()

    @BeforeTest
    fun setup() {
        fileSystem = FakeFileSystem()
        fileSystem.write(propsPath) { writeUtf8(GRADLE_WRAPPER_PROPERTIES) }
    }

    @AfterTest
    fun teardown() {
        fileSystem.checkNoOpenFiles()
        fileSystem.close()
    }

    @Test
    fun testUpdate() {
        runTest(dispatcher) {
            GradleWrapperPropertiesWriter(
                fileSystem = fileSystem,
                ioDispatcher = dispatcher,
                gradleWrapperPropertiesPath = propsPath,
            ).replaceGradleWrapperVersion(
                GradleUpdateInfo(
                    currentVersion = GradleVersion(OLD_VERSION),
                    updatedVersion = GradleVersion(NEW_VERSION),
                    checksum = CHECKSUM,
                )
            )
            advanceUntilIdle()
            assertEquals(
                expected = GRADLE_WRAPPER_PROPERTIES_UPDATED.trimIndent().trim(),
                actual = fileSystem.read(propsPath) { readUtf8().trimIndent().trim() }
            )
        }
    }

    @Test
    fun testSnapshot() {
        runTest(dispatcher) {
            GradleWrapperPropertiesWriter(
                fileSystem = fileSystem,
                ioDispatcher = dispatcher,
                gradleWrapperPropertiesPath = propsPath,
            ).replaceGradleWrapperVersion(
                GradleUpdateInfo(
                    currentVersion = GradleVersion(OLD_VERSION),
                    updatedVersion = GradleVersion(SNAPSHOT_VERSION),
                )
            )
            advanceUntilIdle()
            assertEquals(
                expected = GRADLE_WRAPPER_PROPERTIES_UPDATED_SNAPSHOT.trimIndent().trim(),
                actual = fileSystem.read(propsPath) { readUtf8().trimIndent().trim() }
            )
        }
    }

    companion object {
        private const val OLD_VERSION = "1.0"
        private const val NEW_VERSION = "1.1"
        private const val SNAPSHOT_VERSION = "1.2.0-20260819011813+0000"

        private const val CHECKSUM = "abc123"

        private const val GRADLE_WRAPPER_PROPERTIES = """
        distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-$OLD_VERSION-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists    
        """

        private const val GRADLE_WRAPPER_PROPERTIES_UPDATED = """
        distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionSha256Sum=$CHECKSUM
distributionUrl=https\://services.gradle.org/distributions/gradle-$NEW_VERSION-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists    
        """

        private const val GRADLE_WRAPPER_PROPERTIES_UPDATED_SNAPSHOT = """
        distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions-snapshots/gradle-$SNAPSHOT_VERSION-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists    
        """
    }
}
