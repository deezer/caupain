@file:OptIn(ExperimentalBCVApi::class)

import com.strumenta.antlrkotlin.gradle.AntlrKotlinTask
import io.gitlab.arturbosch.detekt.Detekt
import kotlinx.validation.ExperimentalBCVApi
import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlinx.serialization)
    `maven-publish`
    alias(libs.plugins.kotlinx.atomicfu)
    alias(libs.plugins.dokka)
    alias(libs.plugins.antlr.kotlin)
    alias(libs.plugins.binary.compatibility.validator)
    alias(libs.plugins.compat.patrouille)
    alias(libs.plugins.kotlinx.kover)
}

dependencies {
    "detektPlugins"(libs.detekt.libraries)
}

compatPatrouille {
    java(17)
    kotlin(libs.versions.kotlin.get())
}

kotlin {
    explicitApi()
    compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")

    sourceSets {
        macosX64()
        macosArm64()
        mingwX64()
        linuxX64()
        jvm()

        getByName("commonMain") {
            kotlin {
                srcDir(layout.buildDirectory.dir("generated-src/antlr"))
            }
            dependencies {
                implementation(libs.kotlinx.serialization.xml)
                implementation(libs.kotlinx.serialization.toml)
                api(libs.okio)
                implementation(libs.bundles.ktor)
                implementation(libs.bundles.ktor.serialization)
                implementation(libs.xmlutil.core.io)
                implementation(libs.kotlinx.io.okio)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.html)
                implementation(libs.stately.concurrent.collections)
                implementation(libs.semver)
                implementation(libs.antlr.kotlin.runtime)
            }
        }
        getByName("commonTest") {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.okio.fake.filesystem)
                implementation(libs.ktor.client.mock)
                implementation(libs.kotlinx.coroutines.test)
                compileOnly(libs.jetbrains.annotations)
                implementation(libs.bundles.ktor.test.server)
                implementation(libs.ktor.client.cio)
            }
        }
        getByName("jvmMain") {
            dependencies {
                implementation(libs.slf4j.nop)
                implementation(libs.ktor.client.okhttp)
            }
        }
        getByName("jvmTest") {
            dependencies {
                implementation(libs.kotlin.test.junit)
                implementation(libs.junit)
            }
        }
        create("macosMain") {
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
        create("mingwMain") {
            dependencies {
                implementation(libs.ktor.client.winhttp)
            }
        }
        create("linuxMain") {
            dependencies {
                implementation(libs.ktor.client.curl)
            }
        }
    }
}

val generateKotlinGrammarSource = tasks.register<AntlrKotlinTask>("generateKotlinGrammarSource") {
    dependsOn("cleanGenerateKotlinGrammarSource")
    source = fileTree(layout.projectDirectory.dir("src/antlr")) {
        include("**/*.g4")
    }
    packageName = "com.deezer.caupain.antlr"
    arguments = listOf("-visitor")
    outputDirectory = layout
        .buildDirectory
        .dir("generated-src/antlr/${packageName!!.replace(".", "/")}")
        .get()
        .asFile
}
tasks.withType<KotlinCompilationTask<*>> {
    dependsOn(generateKotlinGrammarSource)
}
tasks
    .matching { it.name.endsWith("SourcesJar", ignoreCase = true) }
    .configureEach { dependsOn(generateKotlinGrammarSource) }
tasks.withType<Detekt> {
    dependsOn(generateKotlinGrammarSource)
    exclude("**/antlr/**")
}

kover {
    reports {
        filters {
            excludes {
                classes("com.deezer.caupain.antlr.*")
            }
        }
    }
}

dokka {
    dokkaSourceSets.configureEach {
        documentedVisibilities(VisibilityModifier.Public)
        suppressedFiles.from(project.layout.buildDirectory.dir("generated-src"))
    }
}

apiValidation {
    ignoredPackages.add("com.deezer.caupain.antlr")
    klib {
        enabled = true
    }
}

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
}

tasks.register("testAll") {
    dependsOn(tasks.withType<Test>(), tasks.withType<KotlinNativeTest>())
}