plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinx.serialization)
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

        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.serialization.xml)
                implementation(libs.kotlinx.serialization.toml)
                api(libs.okio)
                implementation(libs.ktor.client.core)
                api(libs.ktor.client.logging)
                implementation(libs.ktor.client.content.negociation)
                implementation(libs.ktor.serialization.kotlinx.xml)
                implementation(libs.xmlutil.core.io)
                implementation(libs.kotlinx.io.okio)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.html)
                implementation(libs.stately.concurrent.collections)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.okio.fake.filesystem)
                implementation(libs.ktor.client.mock)
                implementation(libs.kotlinx.coroutines.test)
                compileOnly(libs.jetbrains.annotations)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.slf4j.nop)
                implementation(libs.ktor.client.okhttp)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(libs.kotlin.test.junit)
                implementation(libs.junit)
            }
        }
        val macosMain by creating {
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
        val mingwMain by creating {
            dependencies {
                implementation(libs.ktor.client.winhttp)
            }
        }
        val linuxMain by creating {
            dependencies {
                implementation(libs.ktor.client.curl)
            }
        }
    }
}
