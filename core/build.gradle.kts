import com.strumenta.antlrkotlin.gradle.AntlrKotlinTask
import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinx.serialization)
    `maven-publish`
    alias(libs.plugins.kotlinx.atomicfu)
    alias(libs.plugins.dokka)
    alias(libs.plugins.antlr.kotlin)
}

dependencies {
    "detektPlugins"(libs.detekt.libraries)
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

dokka {
    dokkaSourceSets.configureEach {
        documentedVisibilities(VisibilityModifier.Public)
        suppressedFiles.from(project.layout.buildDirectory.dir("generated-src"))
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