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

import com.deezer.caupain.tasks.Sha256ChecksumTask

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.tapmoc)
}

tapmoc {
    java(17)
    kotlin(libs.versions.kotlin.get())
}

kotlin {
    js {
        nodejs()
        useCommonJs()
        binaries.executable()
    }

    sourceSets {
        jsMain {
            dependencies {
                implementation(projects.core)
                implementation(projects.cliConfig)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.okio.node.filesystem)
            }
        }
        jsTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.okio.fake.filesystem)
            }
        }
    }
}

// The action is not published as a package: its bundle is attached to the GitHub releases, and
// downloaded by the action.yml file when the action runs.
val packageAction = tasks.register<Tar>("packageAction") {
    group = "distribution"
    description = "Packages the bundle of the GitHub action, to attach it to releases"
    dependsOn("jsProductionExecutableCompileSync")
    from(layout.buildDirectory.dir("compileSync/js/main/productionExecutable/kotlin")) {
        // Source maps are not needed to run the action, and are by far the largest files
        exclude("**/*.map")
    }
    archiveFileName = "caupain-github-action.tar.gz"
    destinationDirectory = layout.buildDirectory.dir("distributions")
    compression = Compression.GZIP
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
}

val checksumAction = tasks.register<Sha256ChecksumTask>("checksumAction") {
    group = "distribution"
    description = "Computes the checksum of the packaged GitHub action"
    inputFile = packageAction.flatMap { it.archiveFile }
    outputFile = layout.buildDirectory.file("distributions/caupain-github-action.tar.gz.sha256")
}

tasks.register("assembleAction") {
    group = "distribution"
    description = "Builds the GitHub action release files: the archive and its checksum"
    dependsOn(packageAction, checksumAction)
}
