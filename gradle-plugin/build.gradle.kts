plugins {
    `kotlin-dsl`
    `maven-publish`
    `java-gradle-plugin`
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
    plugins {
        create("dependencies") {
            id = "com.deezer.dependencies.update"
            implementationClass = "com.deezer.dependencies.plugin.DependencyUpdatePlugin"
        }
    }
}

afterEvaluate {
    publishing {
        publications {
            named<MavenPublication>("pluginMaven") {
                artifact(
                    tasks.register<Jar>("sourcesJar") {
                        archiveClassifier.convention("sources")
                        archiveClassifier.set("sources")
                        from(sourceSets["main"].allSource)
                    }
                )
            }
        }
    }
}