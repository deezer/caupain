@file:OptIn(ExperimentalAbiValidation::class)

import com.vanniktech.maven.publish.GradlePublishPlugin
import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-gradle-plugin`
    alias(libs.plugins.compat.patrouille)
    alias(libs.plugins.vanniktech.maven.publish)
    alias(libs.plugins.gradle.plugin.publish)
    alias(libs.plugins.dependency.guard)
    alias(libs.plugins.build.config)
    alias(libs.plugins.testkit.support)
}

compatPatrouille {
    java(17)
    kotlin(libs.versions.kotlin.get())
}

buildConfig {
    buildConfigField("VERSION", version.toString())
    useKotlinOutput()
}

kotlin {
    abiValidation {
        enabled.set(true)
    }
}

gradleTestKitSupport {
    withSupportLibrary()
}

dependencies {
    api(projects.core)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    functionalTestImplementation(libs.ktor.http)
    functionalTestImplementation(libs.junit)
    functionalTestImplementation(libs.kotlin.test)
    functionalTestImplementation(libs.kotlin.test.junit)
    functionalTestImplementation(libs.mockwebserver)
    functionalTestImplementation(libs.mockwebserver.junit)
    functionalTestImplementation(libs.kotlinx.coroutines.test)
    functionalTestImplementation(libs.test.parameter.injector)
}

dependencyGuard {
    configuration("runtimeClasspath")
    configuration("compileClasspath")
}

gradlePlugin {
    website = "https://github.com/deezer/caupain"
    vcsUrl = "https://github.com/deezer/caupain"
    plugins {
        create("dependencies") {
            id = "com.deezer.caupain"
            displayName = "Caupain"
            description = "Plugin to check for dependency updates from version catalog"
            tags = listOf("dependencies", "update", "version-catalog")
            implementationClass = "com.deezer.caupain.plugin.DependencyUpdatePlugin"
        }
    }
}

mavenPublishing {
    configure(GradlePublishPlugin())
    if (version.toString().endsWith("-SNAPSHOT")) {
        // Publish snapshots to Maven Central
        publishToMavenCentral(automaticRelease = true)
        signAllPublications()
    }
    pom {
        name = "Caupain Gradle plugin"
        description = "Gradle plugin generating reports for dependency updates."
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

tasks.withType<Detekt> {
    setSource(files("src/main/java"))
}

tasks.named("check") {
    dependsOn("checkLegacyAbi")
}