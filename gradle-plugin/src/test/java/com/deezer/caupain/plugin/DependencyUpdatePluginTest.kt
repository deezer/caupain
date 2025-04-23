package com.deezer.caupain.plugin

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import mockwebserver3.junit4.MockWebServerRule
import okhttp3.ExperimentalOkHttpApi
import okhttp3.Headers
import okio.use
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.intellij.lang.annotations.Language
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.test.assertContains
import kotlin.test.assertEquals

@OptIn(ExperimentalOkHttpApi::class)
class DependencyUpdatePluginTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @get:Rule
    val mockWebserverRule = MockWebServerRule()

    @Before
    fun setup() {
        // Unzip project
        unzipProject()
        // Add repository in build file
        File(tempFolder.root, "build.gradle.kts").writeText(
            createBuildFile(
                repositoryUrl = mockWebserverRule.server.url("maven").toString(),
                gradleUrl = mockWebserverRule.server.url("gradle").toString()
            )
        )
        mockWebserverRule.server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = when (request.path) {
                    "/maven/androidx/core/core-ktx/maven-metadata.xml" -> CORE_KTX_METADATA
                    "/maven/androidx/core/core-ktx/1.17.0/core-ktx-1.17.0.pom" -> CORE_KTX_POM
                    "/maven/org/jetbrains/kotlin/android/org.jetbrains.kotlin.android.gradle.plugin/maven-metadata.xml" -> ANDROID_KOTLIN_PLUGIN_METADATA
                    "/maven/org/jetbrains/kotlin/android/org.jetbrains.kotlin.android.gradle.plugin/2.1.20/org.jetbrains.kotlin.android.gradle.plugin-2.1.20.pom" -> ANDROID_KOTLIN_PLUGIN_POM
                    "/maven/org/jetbrains/kotlin/kotlin-gradle-plugin/2.1.20/kotlin-gradle-plugin-2.1.20.pom" -> ANDROID_KOTLIN_PLUGIN_REAL_POM
                    "/gradle" -> return MockResponse(
                        code = 200,
                        body = GRADLE_VERSION,
                        headers = Headers.headersOf("Content-Type", "application/json")
                    )
                    else -> null
                }
                return if (body == null) {
                    MockResponse(code = 404)
                } else {
                    MockResponse(
                        code = 200,
                        body = body,
                        headers = Headers.headersOf("Content-Type", "application/xml")
                    )
                }
            }
        }
    }

    private fun unzipProject() {
        javaClass
            .getResourceAsStream("fake-project.zip")
            ?.let { ZipInputStream(it) }
            ?.use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val file = File(tempFolder.root, entry.name)
                    if (entry.isDirectory) {
                        file.mkdirs()
                    } else {
                        file.parentFile?.mkdirs()
                        file.outputStream().use { zis.copyTo(it) }
                    }
                    entry = zis.nextEntry
                }
                zis.closeEntry()
            }
    }

    @Language("kotlin")
    private fun createBuildFile(
        repositoryUrl: String,
        gradleUrl: String
    ) = """
    import com.deezer.caupain.model.AndroidXVersionLevelPolicy
    import com.deezer.caupain.plugin.DependenciesUpdateTask
    import com.deezer.caupain.plugin.Policy
    import com.deezer.caupain.model.Repository

    // Top-level build file where you can add configuration options common to all sub-projects/modules.
    plugins {
        alias(libs.plugins.android.application) apply false
        alias(libs.plugins.kotlin.android) apply false
    }

    tasks.withType<DependenciesUpdateTask> {
        selectIf(Policy.from(AndroidXVersionLevelPolicy))
        repositories.set(listOf(Repository("$repositoryUrl")))
        pluginRepositories.set(listOf(Repository("$repositoryUrl")))
        gradleCurrentVersionUrl.set("$gradleUrl")
    }    
    """.trimIndent()

    @Test
    fun testPlugin() {
        val result = GradleRunner
            .create()
            .withProjectDir(tempFolder.root)
            .withArguments(":checkDependencyUpdates", "--no-configuration-cache", "--stacktrace")
            .withPluginClasspath()
            .build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":checkDependencyUpdates")?.outcome)
        assertContains(result.output, EXPECTED_CONSOLE_RESULT)
        assertEquals(
            expected = EXPECTED_HTML_RESULT.trim(),
            actual = File(tempFolder.root, "build/reports/dependency-updates.html")
                .readText()
                .trim()
        )
    }
}

@Language("XML")
private val CORE_KTX_METADATA = """
<?xml version='1.0' encoding='UTF-8'?>
<metadata>
  <groupId>androidx.core</groupId>
  <artifactId>core-ktx</artifactId>
  <versioning>
    <latest>1.17.0</latest>
    <release>1.17.0</release>
    <versions>
      <version>1.12.0</version>
      <version>1.13.0-alpha01</version>
      <version>1.13.0-alpha02</version>
      <version>1.13.0-alpha03</version>
      <version>1.13.0-alpha04</version>
      <version>1.13.0-alpha05</version>
      <version>1.13.0-beta01</version>
      <version>1.13.0-rc01</version>
      <version>1.13.0</version>
      <version>1.13.1</version>
      <version>1.14.0-alpha01</version>
      <version>1.15.0-alpha01</version>
      <version>1.15.0-alpha02</version>
      <version>1.15.0-beta01</version>
      <version>1.15.0-rc01</version>
      <version>1.15.0</version>
      <version>1.16.0-alpha01</version>
      <version>1.16.0-alpha02</version>
      <version>1.16.0-beta01</version>
      <version>1.16.0-rc01</version>
      <version>1.16.0</version>
      <version>1.17.0</version>
    </versions>
    <lastUpdated>20250409170101</lastUpdated>
  </versioning>
</metadata>
""".trimIndent()

@Language("XML")
private val CORE_KTX_POM = """
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <!-- This module was also published with a richer model, Gradle metadata,  -->
  <!-- which should be used instead. Do not delete the following line which  -->
  <!-- is to indicate to Gradle or any Gradle module metadata file consumer  -->
  <!-- that they should prefer consuming it instead. -->
  <!-- do_not_remove: published-with-gradle-metadata -->
  <modelVersion>4.0.0</modelVersion>
  <groupId>androidx.core</groupId>
  <artifactId>core-ktx</artifactId>
  <version>1.17.0</version>
  <packaging>aar</packaging>
  <name>Core Kotlin Extensions</name>
  <description>Kotlin extensions for 'core' artifact</description>
  <url>https://developer.android.com/jetpack/androidx/releases/core#1.17.0</url>
  <inceptionYear>2018</inceptionYear>
  <organization>
    <name>The Android Open Source Project</name>
  </organization>
  <licenses>
    <license>
      <name>The Apache Software License, Version 2.0</name>
      <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <developers>
    <developer>
      <name>The Android Open Source Project</name>
    </developer>
  </developers>
  <scm>
    <connection>scm:git:https://android.googlesource.com/platform/frameworks/support</connection>
    <url>https://cs.android.com/androidx/platform/frameworks/support</url>
  </scm>
  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>org.jetbrains.kotlin</groupId>
        <artifactId>kotlin-stdlib</artifactId>
        <version>1.8.22</version>
      </dependency>
      <dependency>
        <groupId>androidx.core</groupId>
        <artifactId>core</artifactId>
        <version>1.16.0</version>
      </dependency>
    </dependencies>
  </dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>androidx.core</groupId>
      <artifactId>core</artifactId>
      <version>1.16.0</version>
      <scope>compile</scope>
      <type>aar</type>
    </dependency>
    <dependency>
      <groupId>org.jetbrains.kotlin</groupId>
      <artifactId>kotlin-stdlib</artifactId>
      <scope>compile</scope>
    </dependency>
    <dependency>
      <groupId>androidx.annotation</groupId>
      <artifactId>annotation</artifactId>
      <version>1.8.1</version>
      <scope>compile</scope>
    </dependency>
  </dependencies>
</project>
""".trimIndent()

@Language("XML")
private val ANDROID_KOTLIN_PLUGIN_METADATA = """
<?xml version="1.0" encoding="UTF-8"?>
<metadata modelVersion="1.1.0">
  <groupId>org.jetbrains.kotlin.android</groupId>
  <artifactId>org.jetbrains.kotlin.android.gradle.plugin</artifactId>
  <version>2.2.0-Beta1</version>
  <versioning>
    <latest>2.2.0-Beta1</latest>
    <release>2.2.0-Beta1</release>
    <versions>
      <version>0.0.1-test-1</version>
      <version>0.0.1-test-2</version>
      <version>0.1.1.2-5-test-2</version>
      <version>0.1.1.2-5-test-4</version>
      <version>0.1.1.2-5-test-5</version>
      <version>1.1-SNAPSHOT</version>
      <version>1.1.1</version>
      <version>1.1.2</version>
      <version>1.1.2-2</version>
      <version>1.1.2-5</version>
      <version>1.1.3</version>
      <version>1.1.3-2</version>
      <version>1.1.4</version>
      <version>1.1.4-2</version>
      <version>1.1.4-3</version>
      <version>1.1.50</version>
      <version>1.1.51</version>
      <version>1.1.60</version>
      <version>1.1.61</version>
      <version>1.2.0</version>
      <version>1.2.10</version>
      <version>1.2.20</version>
      <version>1.2.21</version>
      <version>1.2.30</version>
      <version>1.2.31</version>
      <version>1.2.40</version>
      <version>1.2.41</version>
      <version>1.2.50</version>
      <version>1.2.51</version>
      <version>1.2.60</version>
      <version>1.2.61</version>
      <version>1.2.70</version>
      <version>1.2.71</version>
      <version>1.3.0-rc-190</version>
      <version>1.3.0-rc-198</version>
      <version>1.3.0</version>
      <version>1.3.10</version>
      <version>1.3.11</version>
      <version>1.3.20</version>
      <version>1.3.21</version>
      <version>1.3.30</version>
      <version>1.3.31</version>
      <version>1.3.40</version>
      <version>1.3.41</version>
      <version>1.3.50</version>
      <version>1.3.60</version>
      <version>1.3.61</version>
      <version>1.3.70</version>
      <version>1.3.71</version>
      <version>1.3.72</version>
      <version>1.4.0-rc</version>
      <version>1.4-SNAPSHOT</version>
      <version>1.4.0</version>
      <version>1.4.10</version>
      <version>1.4.20-M1</version>
      <version>1.4.20-M2</version>
      <version>1.4.20-RC</version>
      <version>1.4.20</version>
      <version>1.4.21</version>
      <version>1.4.21-2</version>
      <version>1.4.30-M1</version>
      <version>1.4.30-RC</version>
      <version>1.4.30</version>
      <version>1.4.31</version>
      <version>1.4.32</version>
      <version>1.4.255-SNAPSHOT</version>
      <version>1.5.0-M1</version>
      <version>1.5.0-M2</version>
      <version>1.5.0-RC</version>
      <version>1.5.0</version>
      <version>1.5.10</version>
      <version>1.5.20-M1</version>
      <version>1.5.20-RC</version>
      <version>1.5.20</version>
      <version>1.5.21</version>
      <version>1.5.30-M1</version>
      <version>1.5.30-RC</version>
      <version>1.5.30</version>
      <version>1.5.31</version>
      <version>1.5.32</version>
      <version>1.5.255-SNAPSHOT</version>
      <version>1.6.0-M1</version>
      <version>1.6.0-RC</version>
      <version>1.6.0-RC2</version>
      <version>1.6.0</version>
      <version>1.6.10-RC</version>
      <version>1.6.10</version>
      <version>1.6.20-M1</version>
      <version>1.6.20-RC</version>
      <version>1.6.20-RC2</version>
      <version>1.6.20</version>
      <version>1.6.21</version>
      <version>1.7.0-Beta</version>
      <version>1.7.0-RC</version>
      <version>1.7.0-RC2</version>
      <version>1.7.0</version>
      <version>1.7.10</version>
      <version>1.7.20-Beta</version>
      <version>1.7.20-RC</version>
      <version>1.7.20</version>
      <version>1.7.21</version>
      <version>1.7.22</version>
      <version>1.7.255-SNAPSHOT</version>
      <version>1.8.0-Beta</version>
      <version>1.8.0-RC</version>
      <version>1.8.0-RC2</version>
      <version>1.8.0</version>
      <version>1.8.10</version>
      <version>1.8.20-Beta</version>
      <version>1.8.20-RC</version>
      <version>1.8.20-RC2</version>
      <version>1.8.20</version>
      <version>1.8.21</version>
      <version>1.8.22</version>
      <version>1.8.255-SNAPSHOT</version>
      <version>1.9.0-Beta</version>
      <version>1.9.0-RC</version>
      <version>1.9.0</version>
      <version>1.9.10</version>
      <version>1.9.20-Beta</version>
      <version>1.9.20-Beta2</version>
      <version>1.9.20-RC</version>
      <version>1.9.20-RC2</version>
      <version>1.9.20</version>
      <version>1.9.21</version>
      <version>1.9.22</version>
      <version>1.9.23</version>
      <version>1.9.24</version>
      <version>1.9.25</version>
      <version>1.9.255-SNAPSHOT</version>
      <version>2.0.0-Beta1</version>
      <version>2.0.0-Beta2</version>
      <version>2.0.0-Beta3</version>
      <version>2.0.0-Beta4</version>
      <version>2.0.0-Beta5</version>
      <version>2.0.0-RC1</version>
      <version>2.0.0-RC2</version>
      <version>2.0.0-RC3</version>
      <version>2.0.0</version>
      <version>2.0.10-RC</version>
      <version>2.0.10-RC2</version>
      <version>2.0.10</version>
      <version>2.0.20-Beta1</version>
      <version>2.0.20-Beta2</version>
      <version>2.0.20-RC</version>
      <version>2.0.20-RC2</version>
      <version>2.0.20</version>
      <version>2.0.21-RC</version>
      <version>2.0.21</version>
      <version>2.0.255-SNAPSHOT</version>
      <version>2.1.0-Beta1</version>
      <version>2.1.0-Beta2</version>
      <version>2.1.0-RC</version>
      <version>2.1.0-RC2</version>
      <version>2.1.0</version>
      <version>2.1.10-RC</version>
      <version>2.1.10-RC2</version>
      <version>2.1.10</version>
      <version>2.1.20-Beta1</version>
      <version>2.1.20-Beta2</version>
      <version>2.1.20-RC</version>
      <version>2.1.20-RC2</version>
      <version>2.1.20-RC3</version>
      <version>2.1.20</version>
      <version>2.1.21-RC</version>
      <version>2.2.0-Beta1</version>
    </versions>
    <lastUpdated>20250417070011</lastUpdated>
  </versioning>
</metadata>    
""".trimIndent()

@Language("XML")
private val ANDROID_KOTLIN_PLUGIN_POM = """
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>org.jetbrains.kotlin.android</groupId>
  <artifactId>org.jetbrains.kotlin.android.gradle.plugin</artifactId>
  <version>2.1.20</version>
  <packaging>pom</packaging>
  <name>Kotlin Android plugin</name>
  <description>Kotlin Android plugin</description>
  <url>https://kotlinlang.org/</url>
  <licenses>
    <license>
      <name>The Apache License, Version 2.0</name>
      <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
    </license>
  </licenses>
  <developers>
    <developer>
      <name>Kotlin Team</name>
      <organization>JetBrains</organization>
      <organizationUrl>https://www.jetbrains.com</organizationUrl>
    </developer>
  </developers>
  <scm>
    <connection>scm:git:https://github.com/JetBrains/kotlin.git</connection>
    <developerConnection>scm:git:https://github.com/JetBrains/kotlin.git</developerConnection>
    <url>https://github.com/JetBrains/kotlin</url>
  </scm>
  <dependencies>
    <dependency>
      <groupId>org.jetbrains.kotlin</groupId>
      <artifactId>kotlin-gradle-plugin</artifactId>
      <version>2.1.20</version>
    </dependency>
  </dependencies>
</project>    
""".trimIndent()

@Language("XML")
private val ANDROID_KOTLIN_PLUGIN_REAL_POM = """
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <!-- This module was also published with a richer model, Gradle metadata,  -->
  <!-- which should be used instead. Do not delete the following line which  -->
  <!-- is to indicate to Gradle or any Gradle module metadata file consumer  -->
  <!-- that they should prefer consuming it instead. -->
  <!-- do_not_remove: published-with-gradle-metadata -->
  <modelVersion>4.0.0</modelVersion>
  <groupId>org.jetbrains.kotlin</groupId>
  <artifactId>kotlin-gradle-plugin</artifactId>
  <version>2.1.20</version>
  <name>Kotlin Gradle Plugin</name>
  <description>Kotlin Gradle Plugin</description>
  <url>https://kotlinlang.org/</url>
  <licenses>
    <license>
      <name>The Apache License, Version 2.0</name>
      <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
    </license>
  </licenses>
  <developers>
    <developer>
      <name>Kotlin Team</name>
      <organization>JetBrains</organization>
      <organizationUrl>https://www.jetbrains.com</organizationUrl>
    </developer>
  </developers>
  <scm>
    <connection>scm:git:https://github.com/JetBrains/kotlin.git</connection>
    <developerConnection>scm:git:https://github.com/JetBrains/kotlin.git</developerConnection>
    <url>https://github.com/JetBrains/kotlin</url>
  </scm>
  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>org.jetbrains.kotlin</groupId>
        <artifactId>kotlin-gradle-plugins-bom</artifactId>
        <version>2.1.20</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.jetbrains.kotlin</groupId>
      <artifactId>kotlin-gradle-plugin-api</artifactId>
      <version>2.1.20</version>
      <scope>compile</scope>
      <exclusions>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-reflect</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-stdlib-jdk8</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-stdlib-jdk7</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-stdlib</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-script-runtime</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-stdlib-common</artifactId>
        </exclusion>
      </exclusions>
    </dependency>
    <dependency>
      <groupId>org.jetbrains.kotlin</groupId>
      <artifactId>kotlin-gradle-plugin-model</artifactId>
      <version>2.1.20</version>
      <scope>compile</scope>
      <exclusions>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-reflect</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-stdlib-jdk8</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-stdlib-jdk7</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-stdlib</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-script-runtime</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-stdlib-common</artifactId>
        </exclusion>
      </exclusions>
    </dependency>
    <dependency>
      <groupId>org.jetbrains.kotlin</groupId>
      <artifactId>fus-statistics-gradle-plugin</artifactId>
      <version>2.1.20</version>
      <scope>compile</scope>
      <exclusions>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-reflect</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-stdlib-jdk8</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-stdlib-jdk7</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-stdlib</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-script-runtime</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-stdlib-common</artifactId>
        </exclusion>
      </exclusions>
    </dependency>
    <dependency>
      <groupId>org.jetbrains.kotlin</groupId>
      <artifactId>kotlin-gradle-plugin-idea</artifactId>
      <version>2.1.20</version>
      <scope>runtime</scope>
      <exclusions>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-reflect</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-stdlib-jdk8</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-stdlib-jdk7</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-stdlib</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-script-runtime</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-stdlib-common</artifactId>
        </exclusion>
      </exclusions>
    </dependency>
    <dependency>
      <groupId>org.jetbrains.kotlin</groupId>
      <artifactId>kotlin-gradle-plugin-idea-proto</artifactId>
      <version>2.1.20</version>
      <scope>runtime</scope>
      <exclusions>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-reflect</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-stdlib-jdk8</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-stdlib-jdk7</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-stdlib</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-script-runtime</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-stdlib-common</artifactId>
        </exclusion>
      </exclusions>
    </dependency>
    <dependency>
      <groupId>org.jetbrains.kotlin</groupId>
      <artifactId>kotlin-klib-commonizer-api</artifactId>
      <version>2.1.20</version>
      <scope>runtime</scope>
      <exclusions>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-reflect</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-stdlib-jdk8</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-stdlib-jdk7</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-stdlib</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-script-runtime</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-stdlib-common</artifactId>
        </exclusion>
      </exclusions>
    </dependency>
    <dependency>
      <groupId>org.jetbrains.kotlin</groupId>
      <artifactId>kotlin-build-statistics</artifactId>
      <version>2.1.20</version>
      <scope>runtime</scope>
      <exclusions>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-reflect</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-stdlib-jdk8</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-stdlib-jdk7</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-stdlib</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-script-runtime</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-stdlib-common</artifactId>
        </exclusion>
      </exclusions>
    </dependency>
    <dependency>
      <groupId>org.jetbrains.kotlin</groupId>
      <artifactId>kotlin-util-klib-metadata</artifactId>
      <version>2.1.20</version>
      <scope>runtime</scope>
      <exclusions>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-reflect</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-stdlib-jdk8</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-stdlib-jdk7</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-stdlib</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-script-runtime</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-stdlib-common</artifactId>
        </exclusion>
      </exclusions>
    </dependency>
    <dependency>
      <groupId>org.jetbrains.kotlin</groupId>
      <artifactId>kotlin-compiler-runner</artifactId>
      <version>2.1.20</version>
      <scope>runtime</scope>
      <exclusions>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-compiler-embeddable</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-reflect</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-stdlib-jdk8</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-stdlib-jdk7</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-stdlib</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-script-runtime</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-stdlib-common</artifactId>
        </exclusion>
      </exclusions>
    </dependency>
  </dependencies>
</project>    
""".trimIndent()

@Language("JSON")
private val GRADLE_VERSION = """
{
  "version" : "99.0.0",
  "buildTime" : "20250225092214+0000",
  "current" : true,
  "snapshot" : false,
  "nightly" : false,
  "releaseNightly" : false,
  "activeRc" : false,
  "rcFor" : "",
  "milestoneFor" : "",
  "broken" : false,
  "downloadUrl" : "https://services.gradle.org/distributions/gradle-8.13-bin.zip",
  "checksumUrl" : "https://services.gradle.org/distributions/gradle-8.13-bin.zip.sha256",
  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-8.13-wrapper.jar.sha256"
}    
""".trimIndent()

@Language("HTML")
private val EXPECTED_HTML_RESULT = """
<html>
  <head>
    <style>
        th,
        td {
          border: 1px solid rgb(160 160 160);
          padding: 8px 10px;
        }
        
        td {
          text-align: center;
        }
        
        tr:nth-of-type(even) {
          background-color: #eee;
        }
        
        table {
          border-collapse: collapse;
          border: 2px solid rgb(140 140 140);
          width: 100%;
        }  
        </style>
  </head>
  <body>
    <h1>Dependency updates</h1>
    <h2>Gradle</h2>
    <p>Gradle current version is 8.13 whereas last version is 99.0.0. See <a href="https://docs.gradle.org/99.0.0/release-notes.html">release note</a>.</p>
    <h2>Libraries</h2>
    <p>
      <table>
        <tr>
          <th>Id</th>
          <th>Name</th>
          <th>Current version</th>
          <th>Updated version</th>
          <th>URL</th>
        </tr>
        <tr>
          <td>androidx.core:core-ktx</td>
          <td>Core Kotlin Extensions</td>
          <td>1.16.0</td>
          <td>1.17.0</td>
          <td><a href="https://developer.android.com/jetpack/androidx/releases/core#1.17.0">https://developer.android.com/jetpack/androidx/releases/core#1.17.0</a></td>
        </tr>
      </table>
    </p>
    <h2>Plugins</h2>
    <p>
      <table>
        <tr>
          <th>Id</th>
          <th>Name</th>
          <th>Current version</th>
          <th>Updated version</th>
          <th>URL</th>
        </tr>
        <tr>
          <td>org.jetbrains.kotlin.android</td>
          <td>Kotlin Gradle Plugin</td>
          <td>2.0.21</td>
          <td>2.1.20</td>
          <td><a href="https://kotlinlang.org/">https://kotlinlang.org/</a></td>
        </tr>
      </table>
    </p>
  </body>
</html>    
""".trimIndent()

private val EXPECTED_CONSOLE_RESULT = """
Updates are available
Gradle: 8.13 -> 99.0.0
Library updates:
- androidx.core:core-ktx: 1.16.0 -> 1.17.0
Plugin updates:
- org.jetbrains.kotlin.android: 2.0.21 -> 2.1.20    
""".trimIndent().trim()