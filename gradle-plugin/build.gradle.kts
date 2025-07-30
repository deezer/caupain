import com.vanniktech.maven.publish.GradlePublishPlugin
import io.gitlab.arturbosch.detekt.Detekt

plugins {
    alias(libs.plugins.kotlin.jvm)
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
    kotlin(pluginLibs.versions.kotlin.get())
}

buildConfig {
    buildConfigField("VERSION", version.toString())
    useKotlinOutput()
}

fun isCheckOrPublish(taskName: String): Boolean {
    return taskName.contains("check", ignoreCase = true) ||
           taskName.contains("publish", ignoreCase = true) ||
           taskName.contains("dependencyGuard", ignoreCase = true)
}

// We need core-compat for build, but IntelliJ has an issue with the dependency, so we still use core
// when not checking or publishing
fun DependencyHandler.applyCoreDependency() {
    if (System.getenv("CI").toBoolean() || gradle.startParameter.taskNames.any { isCheckOrPublish(it) })  {
        api(projects.coreCompat)
    } else {
        gradle.taskGraph.whenReady {
            if (allTasks.any { isCheckOrPublish(it.name) }) {
                api(projects.coreCompat)
            } else {
                api(projects.core)
            }
        }
    }
}

dependencies {
    applyCoreDependency()
    api(projects.coreCompat)
    implementation(pluginLibs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.mockwebserver.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.test.parameter.injector)
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