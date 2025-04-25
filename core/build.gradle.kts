import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinx.serialization)
    `maven-publish`
    alias(libs.plugins.kotlinx.atomicfu)
    alias(libs.plugins.dokka)
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

dokka {
    dokkaSourceSets.configureEach {
        documentedVisibilities(VisibilityModifier.Public)
    }
}

publishing {
    repositories {
        maven {
            name = "github"
            setUrl("https://maven.pkg.github.com/bishiboodsh/caupain")
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