plugins {
    `kotlin-dsl`
    `maven-publish`
    `java-gradle-plugin`
    alias(libs.plugins.compat.patrouille)
    alias(libs.plugins.binary.compatibility.validator)
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
    plugins {
        create("dependencies") {
            id = "com.deezer.caupain"
            implementationClass = "com.deezer.caupain.plugin.DependencyUpdatePlugin"
        }
    }
}

afterEvaluate {
    publishing {
        repositories {
            maven {
                name = "github"
                setUrl("https://maven.pkg.github.com/bishiboosh/caupain")
                credentials {
                    username = System.getenv("GITHUB_ACTOR")
                    password = System.getenv("GITHUB_TOKEN")
                }
            }
        }
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