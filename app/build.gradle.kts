plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.gorisse.thomas.willitfit"
    // SceneView 4.69 AARs declare minCompileSdk 37.
    compileSdk = 37

    defaultConfig {
        // Same Play listing as the 2.x flavor of ar-model-viewer.
        applicationId = "com.gorisse.thomas.willitfit"
        minSdk = 28
        targetSdk = 36
        // Epoch minutes, like the 2.x deploy workflow: 29 908 470 = 2026-11-12, above every
        // 2.x upload (last one 2026-10-28). A release build passes -PversionCode=<minutes now>.
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 29_908_470
        versionName = "3.0.8"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Installs next to the Play version on a test phone.
            applicationIdSuffix = ".debug"
            // Phones and the Apple-silicon emulator are all arm64: a lighter APK for the shared
            // test devices, whose storage runs out first.
            ndk { abiFilters += "arm64-v8a" }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // No signing config here: release signing happens outside this repo.
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(libs.sceneview.ar)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.play.review)
    implementation(libs.install.referrer)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
