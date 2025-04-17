plugins {
    `kotlin-dsl`
    `maven-publish`
}

dependencies {
    implementation(projects.core)
    implementation(libs.kotlinx.coroutines.core)
}

group = "com.deezer.dependencies"
version = "0.0.1-SNAPSHOT"

gradlePlugin {
    plugins {
        create("dependencies") {
            id = "com.deezer.dependencies.update"
            implementationClass = "com.deezer.dependencies.plugin.DependencyUpdatePlugin"
        }
    }
}