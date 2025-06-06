@file:OptIn(ExperimentalBCVApi::class)

import com.strumenta.antlrkotlin.gradle.AntlrKotlinTask
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.SonatypeHost
import io.gitlab.arturbosch.detekt.Detekt
import kotlinx.validation.ExperimentalBCVApi
import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier
import org.jetbrains.dokka.gradle.tasks.DokkaGeneratePublicationTask
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.kotlinx.atomicfu)
    alias(libs.plugins.dokka)
    alias(libs.plugins.antlr.kotlin)
    alias(libs.plugins.binary.compatibility.validator)
    alias(libs.plugins.compat.patrouille)
    alias(libs.plugins.kotlinx.kover)
    alias(libs.plugins.vanniktech.maven.publish)
    alias(libs.plugins.dependency.guard)
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
        linuxArm64()
        jvm()

        applyDefaultHierarchyTemplate()

        commonMain {
            kotlin {
                srcDir(layout.buildDirectory.dir("generated-src/antlr"))
            }
            dependencies {
                implementation(libs.kotlinx.serialization.xml)
                implementation(libs.kotlinx.serialization.toml)
                api(libs.okio)
                implementation(libs.bundles.ktor)
                api(libs.ktor.client.core)
                implementation(libs.bundles.ktor.serialization)
                implementation(libs.xmlutil.core.io)
                implementation(libs.kotlinx.io.okio)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.html)
                implementation(libs.stately.concurrent.collections)
                implementation(libs.semver)
                implementation(libs.antlr.kotlin.runtime)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.serialization.json.okio)
            }
        }
        commonTest {
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
        val nonLinuxArm64Main by creating {
            dependsOn(commonMain.get())
        }
        jvmMain {
            dependsOn(nonLinuxArm64Main)
            dependencies {
                implementation(libs.slf4j.nop)
                implementation(libs.ktor.client.okhttp)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.kotlin.test.junit)
                implementation(libs.junit)
            }
        }
        macosMain {
            dependsOn(nonLinuxArm64Main)
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
        mingwMain {
            dependsOn(nonLinuxArm64Main)
            dependencies {
                implementation(libs.ktor.client.winhttp)
            }
        }
        linuxMain {
            dependencies {
                implementation(libs.ktor.client.curl)
            }
        }
        linuxX64Main.get().dependsOn(nonLinuxArm64Main)
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

detekt {
    config.from(rootProject.layout.projectDirectory.file("code-quality/detekt-core.yml"))
}

dokka {
    dokkaSourceSets.configureEach {
        documentedVisibilities(VisibilityModifier.Public)
        suppressedFiles.from(project.layout.buildDirectory.dir("generated-src"))
    }
}
tasks.withType<DokkaGeneratePublicationTask> {
    dependsOn("fixKMPMetadata")
}

apiValidation {
    ignoredPackages.add("com.deezer.caupain.antlr")
    klib {
        enabled = true
    }
}

dependencyGuard {
    configuration("jvmMainCompileClasspath")
    configuration("jvmMainRuntimeClasspath")
    configuration("metadataCommonMainCompileClasspath")
    configuration("metadataNativeMainCompileClasspath")
}

mavenPublishing {
    configure(
        KotlinMultiplatform(
            javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml"),
            sourcesJar = true
        )
    )
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)
    signAllPublications()
    pom {
        name = "Caupain core library"
        description = "Dependency update check for Gradle version catalog. This is the core library " +
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
    dependsOn(tasks.withType<Test>(), tasks.withType<KotlinNativeTest>())
}