plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.soclear.oneuix.hook"
    ndkVersion = "30.0.16248370"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 33
        consumerProguardFiles("consumer-rules.pro")
        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    // ColorMotion's typed bridge dex is built by :cm-native-bridge and shipped as an asset.
    sourceSets.getByName("main").assets.directories.add(
        project(":cm-native-bridge").layout.buildDirectory.dir("assets").get().asFile.absolutePath
    )

    externalNativeBuild {
        cmake {
            path = file("src/main/c/CMakeLists.txt")
            version = "4.1.2"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }
}

dependencies {
    implementation(project(":common"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.dexkit)
    implementation(libs.kotlinx.serialization.json)

    compileOnly(libs.libxposed.api)
    compileOnly(project(":stub"))
}

tasks.named("preBuild") { dependsOn(":cm-native-bridge:bridgeAssets") }
