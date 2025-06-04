import com.vanniktech.maven.publish.GradlePublishPlugin
import com.vanniktech.maven.publish.SonatypeHost
import io.gitlab.arturbosch.detekt.Detekt

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    alias(libs.plugins.compat.patrouille)
    alias(libs.plugins.binary.compatibility.validator)
    alias(libs.plugins.vanniktech.maven.publish)
    alias(libs.plugins.gradle.plugin.publish)
    alias(libs.plugins.dependency.guard)
    alias(libs.plugins.build.config)
}

compatPatrouille {
    java(17)
    kotlin(libs.versions.kotlin.get())
}

buildConfig {
    buildConfigField("VERSION", version.toString())
    useKotlinOutput()
}

dependencies {
    api(projects.core)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.mockwebserver.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

dependencyGuard {
    configuration("runtimeClasspath")
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
        publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)
        signAllPublications()
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
}

tasks.withType<Detekt> {
    setSource(files("src/main/java"))
}