plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.divintegrity.player"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.divintegrity.player"
        minSdk = 26
        targetSdk = 34
        versionCode = 15
        versionName = "1.5.0"
        buildConfigField("String", "AUTH_BASE", "\"https://div-auth.onrender.com\"")
        buildConfigField("String", "DEEP_A_BASE", "\"https://div-scan-deep-a.onrender.com\"")
        buildConfigField("String", "DARK_A_BASE", "\"https://div-scan-dark-a.onrender.com\"")
        buildConfigField("String", "LINK_BASE", "\"https://div-user-link.onrender.com\"")
    }

    buildTypes {
        release { isMinifyEnabled = false }
        debug { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.google.code.gson:gson:2.10.1")
}
