plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.dsh.harness"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dsh.harness"
        minSdk = 26
        targetSdk = 36
        versionCode = 13
        versionName = "1.12"
    }

    signingConfigs {
        create("release") {
            storeFile = file("C:/dsh-android/release.keystore")
            storePassword = "harness-dsh"
            keyAlias = "dsh"
            keyPassword = "harness-dsh"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.core:core-ktx:1.17.0")
    // Markdown rendering (Mikepenz), ported from gpt_mobile reference
    implementation("com.mikepenz:multiplatform-markdown-renderer-m3-android:0.41.0")
    implementation("com.mikepenz:multiplatform-markdown-renderer-code:0.41.0")
}
