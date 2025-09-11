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

import com.autonomousapps.kit.AbstractGradleProject
import com.autonomousapps.kit.GradleProject
import com.autonomousapps.kit.Source
import com.autonomousapps.kit.Subproject
import com.autonomousapps.kit.android.AndroidManifest
import com.autonomousapps.kit.gradle.Dependency
import com.autonomousapps.kit.gradle.GradleProperties
import com.autonomousapps.kit.gradle.Imports
import com.autonomousapps.kit.gradle.Plugin
import com.autonomousapps.kit.gradle.android.AndroidBlock
import com.autonomousapps.kit.gradle.android.CompileOptions
import com.autonomousapps.kit.gradle.android.DefaultConfig
import com.autonomousapps.kit.gradle.android.KotlinOptions
import org.gradle.api.JavaVersion
import org.intellij.lang.annotations.Language
import java.io.File
import java.util.zip.ZipInputStream

class TestProject(
    private val filteredPluginRepositoryUrl: String,
    private val fallbackPluginRepositoryUrl: String,
    private val repositoryUrl: String,
    private val forbiddenRepositoryUrl: String,
    private val htmlOutputFile: File,
    private val markdownOutputFile: File,
    private val jsonOutputFile: File,
    private val supplementaryCaupainConfiguration: String = "",
    private val useBaseSettings: Boolean = false,
) : AbstractGradleProject() {

    private val pluginVersion = PLUGIN_UNDER_TEST_VERSION

    fun build(): GradleProject {
        return newGradleProjectBuilder(GradleProject.DslKind.KOTLIN)
            .withRootProject {
                withVersionCatalog(createVersionCatalog())
                gradleProperties = GradleProperties.minimalAndroidProperties()
                if (useBaseSettings) {
                    withSettingsScript {
                        rootProjectName = "Fake project"
                        subprojects = setOf("app")
                    }
                } else {
                    withFile(
                        path = "settings.gradle.kts",
                        content = createSettingsFile(
                            filteredPluginRepositoryUrl,
                            fallbackPluginRepositoryUrl
                        )
                    )
                }
                withBuildScript {
                    imports = Imports.of(
                        "com.deezer.caupain.model.StabilityLevelPolicy",
                        "com.deezer.caupain.plugin.DependenciesUpdateTask",
                        "com.deezer.caupain.plugin.Policy",
                        "com.deezer.caupain.model.Repository"
                    )
                    plugins(
                        Plugin.of("com.android.application", "8.9.1", false),
                        Plugin.of("org.jetbrains.kotlin.android", "2.0.21", false),
                        Plugin.of("com.deezer.caupain", pluginVersion, true)
                    )
                    withKotlin(
                        buildString {
                            appendLine(
                                createExtensionCode(
                                    repositoryUrl = repositoryUrl,
                                    forbiddenRepositoryUrl = forbiddenRepositoryUrl,
                                    htmlOutputFile = htmlOutputFile,
                                    markdownOutputFile = markdownOutputFile,
                                    jsonOutputFile = jsonOutputFile,
                                    supplementaryConfiguration = supplementaryCaupainConfiguration
                                )
                            )
                            appendLine(
                                """
                                        tasks.withType<DependenciesUpdateTask> {
                                            selectIf(StabilityLevelPolicy)
                                            customFormatter { info ->
                                                println("Infos size : " + info.updateInfos.size)
                                            }   
                                        }
                                """.trimIndent()
                            )
                        }
                    )
                }
            }
            .withAndroidSubproject("app") {
                withBuildScript {
                    plugins(
                        Plugin.of("com.android.application"),
                        Plugin.of("org.jetbrains.kotlin.android")
                    )
                    android = AndroidBlock(
                        namespace = "com.deezer.fakeproject",
                        compileSdkVersion = 35,
                        defaultConfig = DefaultConfig(
                            applicationId = "com.deezer.fakeproject",
                            minSdkVersion = 24,
                            targetSdkVersion = 35,
                            versionCode = 1,
                            versionName = "1.0"
                        ),
                        compileOptions = CompileOptions(
                            sourceCompatibility = JavaVersion.VERSION_11,
                            targetCompatibility = JavaVersion.VERSION_11
                        ),
                        kotlinOptions = KotlinOptions("11")
                    )
                    dependencies(
                        Dependency.versionCatalog("implementation", "libs.androidx.core.ktx"),
                        Dependency.versionCatalog("implementation", "libs.androidx.activity.ktx"),
                    )
                }
                sources = listOf(Source.kotlin(KOTLIN_SOURCE).build())
                manifest = AndroidManifest.app(
                    activities = listOf("com.deezer.fakeproject.MainActivity"),
                )

            }
            .write()
    }
}

internal fun createVersionCatalog(updated: Boolean = false) = """
    [versions]
    agp = "8.9.1"
    kotlin = "${if (updated) "2.1.20" else "2.0.21"}"
    coreKtx = "${if (updated) "1.17.0" else "1.16.0"}"
    activityKtx = "1.10.1"
    ignored = "1.0.0" #ignoreUpdates
    
    [libraries]
    androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
    androidx-activity-ktx = { group = "androidx.activity", name = "activity-ktx", version.ref = "activityKtx" }
    ignored = { group = "com.example", name = "ignored", version.ref = "ignored" }
    
    [plugins]
    android-application = { id = "com.android.application", version.ref = "agp" }
    kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }    
    """.trimIndent()

private val FUNCTIONAL_TEST_REPO: String
    get() = "maven(url = \"${AbstractGradleProject.FUNC_TEST_REPO.replace("\\", "\\\\")}\")"

private fun createSettingsFile(
    filteredRepositoryUrl: String,
    fallbackRepositoryUrl: String
) = """
    pluginManagement {
        repositories {
            $FUNCTIONAL_TEST_REPO
            google {
                content {
                    includeGroupByRegex("com\\.android.*")
                    includeGroupByRegex("com\\.google.*")
                    includeGroupByRegex("androidx.*")
                }
            }
            mavenCentral()
            gradlePluginPortal()
            mavenLocal()
        }
    }
    
    dependencyResolutionManagement {
        repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
        repositories {
            $FUNCTIONAL_TEST_REPO
            maven {
                setUrl("$filteredRepositoryUrl")
                content { 
                    includeGroupAndSubgroups("androidx.core")
                }
                credentials {
                    username = "user"
                    password = "password"
                }
                isAllowInsecureProtocol = true
            }
            maven {
                setUrl("$fallbackRepositoryUrl")
                credentials(HttpHeaderCredentials::class) { 
                    name = "X-Specific-Header"
                    value = "value"
                }
                isAllowInsecureProtocol = true
            }
        }
    }
    
    rootProject.name = "Fake project"
    include(":app")
    """.trimIndent()

@Language("kotlin")
private fun createExtensionCode(
    repositoryUrl: String,
    forbiddenRepositoryUrl: String,
    htmlOutputFile: File,
    markdownOutputFile: File,
    jsonOutputFile: File,
    supplementaryConfiguration: String = ""
) = """
caupain {
    repositories {
        plugins {
            repository("$forbiddenRepositoryUrl") {
                exclude("**")
            }
            repository("$repositoryUrl") {
                headerCredentials {
                    name = "X-Plugin-Header"
                    value = "value"
                }
            }
        }
    }
    outputs {
        console {
            enabled.set(true)
        }
        html {
            enabled.set(true)
            outputFile.set(File("${htmlOutputFile.canonicalPath}"))
        }
        markdown {
            enabled.set(true)
            outputFile.set(File("${markdownOutputFile.canonicalPath}"))
        }
        json {
            enabled.set(true)
            outputFile.set(File("${jsonOutputFile.canonicalPath}"))
        }
    }
    showVersionReferences.set(true)
    checkIgnored.set(true)
    $supplementaryConfiguration
}
""".trimIndent()

private val KOTLIN_SOURCE = """
package com.deezer.fakeproject

import android.os.Bundle
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
}    
""".trimIndent()

private val MANIFEST = """
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <application
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.FakeProject"
        tools:targetApi="31">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:theme="@style/Theme.FakeProject">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />

                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>    
""".trimIndent()

private fun Subproject.Builder.withZip(basePath: String, zipFileName: String) {
    TestProject::class.java
        .getResourceAsStream(zipFileName)
        ?.let { ZipInputStream(it) }
        ?.use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    withFile(
                        path = "$basePath/${entry.name}",
                        content = zis.readBytes().toString(Charsets.UTF_8)
                    )
                }
                entry = zis.nextEntry
            }
            zis.closeEntry()
        }
}