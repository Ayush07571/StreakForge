plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
val vCode = runNumber.coerceAtLeast(1)

val gitRefName = System.getenv("GITHUB_REF_NAME") ?: ""
val vName = when {
    gitRefName.startsWith("v") -> gitRefName.substring(1)
    gitRefName.isNotBlank() -> gitRefName
    else -> "1.0.0"
}

val ksPath = System.getenv("KEYSTORE_PATH")
val ksPassword = System.getenv("KEYSTORE_PASSWORD")
val kAlias = System.getenv("KEY_ALIAS")
val kPassword = System.getenv("KEY_PASSWORD")

val isReleaseSigningConfigured = !ksPath.isNullOrBlank() &&
        !ksPassword.isNullOrBlank() &&
        !kAlias.isNullOrBlank() &&
        !kPassword.isNullOrBlank() &&
        file(ksPath).exists()

android {
    namespace = "com.ayush.streakforge"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ayush.streakforge"
        minSdk = 26
        targetSdk = 34
        versionCode = vCode
        versionName = vName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GITHUB_CLIENT_ID", "\"Ov23liIGs0eOVrGPITrT\"")
    }

    signingConfigs {
        if (isReleaseSigningConfigured) {
            create("release") {
                storeFile = file(ksPath)
                storePassword = ksPassword
                keyAlias = kAlias
                keyPassword = kPassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (isReleaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.glance:glance-appwidget:1.1.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // Security Encrypted Storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // ViewModel Compose & Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")

    // Testing
    testImplementation("junit:junit:4.13.2")
}
