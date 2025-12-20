plugins {
    kotlin("multiplatform") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}

group = "com.musicalchairs"
version = "1.0.0"

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
kotlin {
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "musicalchairs.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        val wasmJsMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.ui)
                implementation(compose.material)
            }
        }
    }
}
