plugins {
    `kotlin-dsl`
    `maven-publish`
}

dependencies {
    api(projects.core)
    implementation(libs.kotlinx.coroutines.core)
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