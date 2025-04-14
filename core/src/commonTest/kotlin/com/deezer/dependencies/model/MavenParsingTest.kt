package com.deezer.dependencies.model

import com.deezer.dependencies.model.maven.Dependency
import com.deezer.dependencies.model.maven.MavenInfo
import com.deezer.dependencies.model.maven.Metadata
import com.deezer.dependencies.model.maven.Version
import com.deezer.dependencies.model.maven.Versioning
import com.deezer.dependencies.serialization.DefaultXml
import kotlinx.serialization.decodeFromString
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
                )
            ),
            actual = DefaultXml.decodeFromString(INFO)
        )
    }
}

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