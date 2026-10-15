plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.lockclock"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pumyk.lockclockswitcher"
        minSdk = 29
        targetSdk = 34
        versionCode = 2
        versionName = "1.0"
    }
    signingConfigs {
        create("release") {
            storeFile = System.getenv("RELEASE_KEYSTORE_FILE")?.let { file(it) }
            storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("RELEASE_KEY_ALIAS")
            keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (System.getenv("RELEASE_KEYSTORE_FILE") != null)
                signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources.excludes += listOf("META-INF/{AL2.0,LGPL2.1}", "META-INF/versions/9/OSGI-INF/MANIFEST.MF") }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Wireless-debugging pairing / ADB client (all via JitPack)
    // JitPack metadata for this artifact is inconsistent: sometimes it declares
    // bcprov-jdk15to18:1.81, sometimes bcprov-jdk18on:1.78.1. Exclude whatever
    // BouncyCastle it transitively pulls - we provide a single copy below.
    implementation("com.github.MuntashirAkon:libadb-android:3.1.0") {
        exclude(group = "org.bouncycastle")
    }
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
    implementation("org.conscrypt:conscrypt-android:2.5.3")
}
