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

import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.SourcesJar
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.tapmoc)
    alias(libs.plugins.vanniktech.maven.publish)
    alias(libs.plugins.dependency.guard)
    alias(libs.plugins.poko)
    alias(libs.plugins.testkit.support)
}

dependencies {
    "detektPlugins"(libs.detekt.libraries)
}

tapmoc {
    java(17)
    kotlin(libs.versions.kotlin.get())
}

kotlin {
    explicitApi()

    sourceSets {
        macosArm64()
        mingwX64()
        linuxX64()
        linuxArm64()
        jvm()
        js {
            nodejs()
        }

        applyDefaultHierarchyTemplate()

        commonMain {
            dependencies {
                implementation(libs.kotlinx.serialization.toml)
            }
        }
    }
}

detekt {
    config.from(rootProject.layout.projectDirectory.file("code-quality/detekt-core.yml"))
}

dependencyGuard {
    configuration("jvmMainCompileClasspath")
    configuration("jvmMainRuntimeClasspath")
    configuration("metadataCommonMainCompileClasspath")
    configuration("metadataNativeMainCompileClasspath")
}

val fromForkedPullRequest = providers
    .gradleProperty("fromForkedPullRequest")
    .map { it.toBoolean() }

mavenPublishing {
    configure(
        KotlinMultiplatform(
            sourcesJar = SourcesJar.Sources()
        )
    )
    publishToMavenCentral(automaticRelease = true)
    if (!fromForkedPullRequest.getOrElse(false)) {
        // Do not sign if the build is from a forked PR because secrets are not available
        signAllPublications()
    }
    pom {
        name = "Caupain tools library"
        description = "Tools used by the core Caupain library"
        inceptionYear = "2025"
        url = "https://github.com/deezer/caupain"
        licenses {
            license {
                name = "The MIT License"
                url = "https://opensource.org/license/mit"
                distribution = url
            }
        }
        developers {
            developer {
                id = "deezer-android-team"
                name = "Deezer Android Team"
                url = "https://github.com/deezer"
            }
        }
        scm {
            url = "https://github.com/deezer/caupain"
            connection = "scm:git:git://github.com/deezer/caupain.git"
            developerConnection = "scm:git:ssh://git@github.com:deezer/caupain.git"
        }
    }
}

tasks.register("testAll") {
    dependsOn(tasks.withType<Test>(), tasks.withType<KotlinNativeTest>())
}
