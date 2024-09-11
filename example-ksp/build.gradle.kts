plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.ksp)
    id("kotlin-parcelize")
}

android {
    namespace = "com.cakcaraka.databinarycompatible.example_ksp"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        targetSdk = 34

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {}
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":annotation"))
    ksp(project(":processor"))
}

ksp {
    arg("data_binary_compatible_required_suffix", "")
    arg("data_binary_compatible_drop_packages_suffix","data")
}