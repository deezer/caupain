plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    sourceSets {
        macosX64()
        mingwX64()
        linuxX64()
        jvm()

        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.serialization.xml)
                implementation(libs.kotlinx.serialization.toml)
                implementation(libs.okio)
                implementation(libs.okio.fake.filesystem)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.client.content.negociation)
                implementation(libs.ktor.serialization.kotlinx.xml)
                implementation(libs.xmlutil.core.io)
                implementation(libs.kotlinx.io.okio)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
        getByName("jvmTest") {
            dependencies {
                implementation(libs.kotlin.test.junit)
                implementation(libs.junit)
            }
        }
    }
}
