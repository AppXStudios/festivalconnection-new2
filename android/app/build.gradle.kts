plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.appxstudios.festivalconnection"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.appxstudios.festivalconnection"
        minSdk = 26
        targetSdk = 35
        versionCode = 9
        versionName = "1.7.0"
        buildConfigField("String", "BREEZ_API_KEY", "\"MIIBeTCCASugAwIBAgIHPxUDqQ19mTAFBgMrZXAwEDEOMAwGA1UEAxMFQnJlZXowHhcNMjYwNDA3MjMwMTM5WhcNMzYwNDA0MjMwMTM5WjAvMRQwEgYDVQQKEwtBUFBYU3R1ZGlvczEXMBUGA1UEAxMOQWJyYWhhbSBWYXJnYXMwKjAFBgMrZXADIQDQg\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.google.accompanist:accompanist-permissions:0.36.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("com.google.zxing:core:3.5.3")
    // CameraX for QR scanning
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.bouncycastle:bcprov-jdk15on:1.70")
    implementation("breez_sdk_liquid:bindings-android:0.11.13")
    // BIP-39 mnemonic generation (https://github.com/Electric-Coin-Company/kotlin-bip39)
    implementation("cash.z.ecc.android:kotlin-bip39:1.0.8")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
