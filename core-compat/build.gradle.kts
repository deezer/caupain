import com.vanniktech.maven.publish.KotlinMultiplatform
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.kotlinx.atomicfu)
    alias(libs.plugins.compat.patrouille)
    alias(libs.plugins.vanniktech.maven.publish)
    alias(libs.plugins.cashapp.burst)
}

compatPatrouille {
    java(17)
    kotlin(pluginLibs.versions.kotlin.get())
}

kotlin {
    explicitApi()
    compilerOptions.freeCompilerArgs.addAll(
        "-Xexpect-actual-classes",
        "-Xwhen-guards"
    )

    sourceSets {
        jvm()

        applyDefaultHierarchyTemplate()

        commonMain {
            kotlin {
                srcDir("../core/src/commonMain/kotlin")
                srcDir("../core/build/generated-src/antlr")
            }
            dependencies {
                implementation(pluginLibs.kotlin.stdlib)
                implementation(pluginLibs.kotlinx.serialization.xml)
                implementation(pluginLibs.kotlinx.serialization.toml)
                api(pluginLibs.okio)
                implementation(libs.bundles.ktor)
                api(libs.ktor.client.core)
                implementation(libs.bundles.ktor.serialization)
                implementation(pluginLibs.xmlutil.core.io)
                implementation(pluginLibs.kotlinx.io.okio)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.html)
                implementation(libs.stately.concurrent.collections)
                implementation(libs.semver)
                implementation(libs.antlr.kotlin.runtime)
                implementation(pluginLibs.kotlinx.serialization.json)
                implementation(pluginLibs.kotlinx.serialization.json.okio)
            }
        }
        commonTest {
            kotlin {
                srcDir("../core/src/commonTest/kotlin")
            }
            dependencies {
                implementation(pluginLibs.kotlin.test)
                implementation(pluginLibs.okio.fake.filesystem)
                implementation(libs.ktor.client.mock)
                implementation(libs.kotlinx.coroutines.test)
                compileOnly(libs.jetbrains.annotations)
                implementation(libs.bundles.ktor.test.server)
                implementation(libs.ktor.client.cio)
            }
        }
        jvmMain {
            kotlin {
                srcDir("../core/src/jvmMain/kotlin")
                srcDir("../core/src/nonLinuxArm64Main/kotlin")
            }
            dependencies {
                implementation(libs.slf4j.nop)
                implementation(libs.ktor.client.okhttp)
            }
        }
        jvmTest {
            kotlin {
                srcDir("../core/src/jvmTest/kotlin")
            }
            resources.srcDir("../core/src/jvmTest/resources")
            dependencies {
                implementation(pluginLibs.kotlin.test.junit)
                implementation(libs.junit)
            }
        }
    }
}

val generateKotlinGrammarSource =
    rootProject.project(":core").tasks.named("generateKotlinGrammarSource")
tasks.withType<KotlinCompilationTask<*>> {
    dependsOn(generateKotlinGrammarSource)
}
tasks
    .matching { it.name.endsWith("SourcesJar", ignoreCase = true) }
    .configureEach { dependsOn(generateKotlinGrammarSource) }

mavenPublishing {
    configure(KotlinMultiplatform(sourcesJar = true))
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()
    pom {
        name = "Caupain core compat library"
        description =
            "Dependency update check for Gradle version catalog. This is the core library " +
                    "used by the CLI tool and the Gradle plugin."
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

tasks.register("testAll") {
    dependsOn(tasks.withType<Test>())
}