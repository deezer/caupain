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

package com.deezer.caupain.model

import com.deezer.caupain.model.maven.Dependency
import com.deezer.caupain.model.maven.MavenInfo
import com.deezer.caupain.model.maven.Metadata
import com.deezer.caupain.model.maven.SCMInfos
import com.deezer.caupain.model.maven.SnapshotVersion
import com.deezer.caupain.model.maven.Version
import com.deezer.caupain.model.maven.Versioning
import com.deezer.caupain.serialization.xml.DefaultXml
import kotlinx.serialization.decodeFromString
import org.intellij.lang.annotations.Language
import kotlin.test.Test
import kotlin.test.assertEquals

class MavenParsingTest {

    @Test
    fun testMetadataParsing() {
        assertEquals(
            expected = Metadata(
                versioning = Versioning(
                    latest = GradleDependencyVersion.Exact("1.1.1"),
                    release = GradleDependencyVersion.Exact("1.1.1"),
                    versions = listOf(
                        Version(GradleDependencyVersion.Exact("1.0.0-alpha4")),
                        Version(GradleDependencyVersion.Exact("1.1.1")),
                    )
                )
            ),
            actual = DefaultXml.decodeFromString(METADATA)
        )
    }

    @Test
    fun testInfoParsing() {
        assertEquals(
            expected = MavenInfo(
                name = "Android Arch-Common",
                url = "https://developer.android.com/topic/libraries/architecture/index.html",
                dependencies = listOf(
                    Dependency(
                        groupId = "junit",
                        artifactId = "junit",
                        version = "4.12"
                    )
                ),
                scm = SCMInfos("http://source.android.com"),
            ),
            actual = DefaultXml.decodeFromString(INFO)
        )
    }

    @Test
    fun testInfoWithPropertiesParsing() {
        assertEquals(
            expected = MavenInfo(
                name = "Android Arch-Common",
                url = "https://developer.android.com/topic/libraries/architecture/index.html",
                dependencies = listOf(
                    Dependency(
                        groupId = "junit",
                        artifactId = "junit",
                        version = "4.12"
                    )
                ),
                scm = SCMInfos("http://source.android.com"),
            ),
            actual = DefaultXml.decodeFromString(INFO_WITH_PROPERTY)
        )
    }

    @Test
    fun testSnapshotParsing() {
        assertEquals(
            expected = Metadata(
                versioning = Versioning(
                    snapshotVersions = listOf(
                        SnapshotVersion(
                            extension = "pom",
                            value = GradleDependencyVersion.Exact("4.0.0-beta-2-20240702.052209-2")
                        ),
                        SnapshotVersion(
                            extension = "jar",
                            value = GradleDependencyVersion.Exact("4.0.0-beta-2-20240702.052209-2")
                        ),
                    )
                )
            ),
            actual = DefaultXml.decodeFromString(SNAPSHOT_METADATA)
        )
    }
}

@Language("XML")
private const val METADATA = """
<metadata modelVersion="1.1.0">
    <groupId>android.arch.core</groupId>
    <artifactId>common</artifactId>
    <versioning>
        <latest>1.1.1</latest>
        <release>1.1.1</release>
        <versions>
            <version>1.0.0-alpha4</version>
            <version>1.1.1</version>
        </versions>
        <lastUpdated>20180321152433</lastUpdated>
    </versioning>
</metadata>
"""

@Language("XML")
private const val INFO = """
<project xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd" xmlns="http://maven.apache.org/POM/4.0.0"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
    <modelVersion>4.0.0</modelVersion>
    <groupId>android.arch.core</groupId>
    <artifactId>common</artifactId>
    <version>1.1.1</version>
    <name>Android Arch-Common</name>
    <description>Android Arch-Common</description>
    <url>https://developer.android.com/topic/libraries/architecture/index.html</url>
    <inceptionYear>2017</inceptionYear>
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
        <url>http://source.android.com</url>
    </scm>
    <dependencies>
        <dependency>
            <groupId>junit</groupId>
            <artifactId>junit</artifactId>
            <version>4.12</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
"""

@Language("XML")
private const val INFO_WITH_PROPERTY = $$"""
<project xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd" xmlns="http://maven.apache.org/POM/4.0.0"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
    <modelVersion>4.0.0</modelVersion>
    <groupId>android.arch.core</groupId>
    <artifactId>common</artifactId>
    <version>1.1.1</version>
    <name>Android Arch-Common</name>
    <description>Android Arch-Common</description>
    <url>https://developer.android.com/topic/libraries/architecture/index.html</url>
    <inceptionYear>2017</inceptionYear>
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
        <url>http://source.android.com</url>
    </scm>
    <properties>
        <junit.version>4.12</junit.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>junit</groupId>
            <artifactId>junit</artifactId>
            <version>${junit.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
"""

@Language("XML")
private val SNAPSHOT_METADATA = """
<?xml version="1.0" encoding="UTF-8"?>
<metadata modelVersion="1.1.0">
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-jar-plugin</artifactId>
  <versioning>
    <lastUpdated>20240702052209</lastUpdated>
    <snapshot>
      <timestamp>20240702.052209</timestamp>
      <buildNumber>2</buildNumber>
    </snapshot>
    <snapshotVersions>
      <snapshotVersion>
        <extension>pom</extension>
        <value>4.0.0-beta-2-20240702.052209-2</value>
        <updated>20240702052209</updated>
      </snapshotVersion>
      <snapshotVersion>
        <extension>jar</extension>
        <value>4.0.0-beta-2-20240702.052209-2</value>
        <updated>20240702052209</updated>
      </snapshotVersion>
    </snapshotVersions>
  </versioning>
  <version>4.0.0-beta-2-SNAPSHOT</version>
</metadata>
""".trimIndent()
