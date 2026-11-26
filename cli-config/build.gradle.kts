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
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.tapmoc)
}

tapmoc {
    java(17)
    kotlin(libs.versions.kotlin.get())
}

kotlin {
    compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")

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
                api(projects.core)
                implementation(libs.kotlinx.serialization.toml)
                implementation(libs.kotlinx.serialization.properties)
                implementation(libs.kotlinx.io.okio)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.okio.fake.filesystem)
                compileOnly(libs.jetbrains.annotations)
            }
        }
    }
}
