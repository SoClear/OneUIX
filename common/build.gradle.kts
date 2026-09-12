plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.soclear.oneuix.common"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 33
        buildConfigField(
            "String",
            "MODULE_APPLICATION_ID",
            "\"${providers.gradleProperty("oneuix.applicationId").get()}\"",
        )
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    compileOnly(project(":stub"))
}
