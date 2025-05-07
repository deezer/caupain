import com.vanniktech.maven.publish.GradlePublishPlugin

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    alias(libs.plugins.compat.patrouille)
    alias(libs.plugins.binary.compatibility.validator)
    alias(libs.plugins.vanniktech.maven.publish)
    alias(libs.plugins.gradle.plugin.publish)
}

compatPatrouille {
    java(17)
    kotlin(libs.versions.kotlin.get())
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
}