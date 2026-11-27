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

// The Kotlin/JS compiler does not minify the code for Node.js, and the modules are three times
// smaller once minified. The bundle is minified module by module, without bundling them, so that
// they are loaded exactly like the ones of the compiler.
//
// The minifier (esbuild) is a build tool, which is not used by the code of the action. It is
// declared in its own npm project, in the tools directory, and not as a dependency of the
// Kotlin/JS module, so that it is not part of the lockfile and the installation of the other
// modules. This needs node and npm to be installed, which they are on GitHub-hosted runners.
val isWindows = System.getProperty("os.name").startsWith("Windows")
val toolsDir = layout.projectDirectory.dir("tools")
val esbuildScript = toolsDir.file("node_modules/esbuild/bin/esbuild")
val compiledBundleDir = layout.buildDirectory.dir("compileSync/js/main/productionExecutable/kotlin")
val minifiedBundleDir = layout.buildDirectory.dir("minifiedAction")
val distributionGroup = "distribution"

val installBuildTools = tasks.register<Exec>("installBuildTools") {
    description = "Installs the tools used to build the GitHub action"
    workingDir(toolsDir)
    inputs.files(toolsDir.file("package.json"), toolsDir.file("package-lock.json"))
    outputs.dir(toolsDir.dir("node_modules"))
    commandLine(
        if (isWindows) "npm.cmd" else "npm",
        // ci installs exactly what the lockfile says, and fails if it is out of date. Scripts are
        // not needed, and not run for safety.
        "ci",
        "--ignore-scripts",
        "--no-audit",
        "--no-fund"
    )
}

val minifyAction = tasks.register<Exec>("minifyAction") {
    group = distributionGroup
    description = "Minifies the bundle of the GitHub action"
    dependsOn("jsProductionExecutableCompileSync", installBuildTools)
    val modules = fileTree(compiledBundleDir) {
        // Source maps are not needed to run the action, and are by far the largest files
        include("*.js")
    }
    inputs.files(modules)
    inputs.file(esbuildScript)
    outputs.dir(minifiedBundleDir)
    doFirst {
        delete(minifiedBundleDir)
        commandLine(
            buildList {
                add("node")
                add(esbuildScript.asFile.absolutePath)
                addAll(modules.files.map { it.absolutePath })
                add("--minify")
                add("--outdir=${minifiedBundleDir.get().asFile.absolutePath}")
                // Warnings are about the code of the dependencies, which is not ours to fix
                add("--log-level=error")
            }
        )
    }
}

// The action is not published as a package: its bundle is attached to the GitHub releases, and
// downloaded by the action.yml file when the action runs.
val packageAction = tasks.register<Tar>("packageAction") {
    group = distributionGroup
    description = "Packages the bundle of the GitHub action, to attach it to releases"
    from(minifyAction)
    archiveFileName = "caupain-github-action.tar.gz"
    destinationDirectory = layout.buildDirectory.dir("distributions")
    compression = Compression.GZIP
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
}

tasks.register("assembleAction") {
    group = distributionGroup
    description = "Builds the GitHub action release file: the archive"
    dependsOn(packageAction)
}
