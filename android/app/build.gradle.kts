plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.synthesia.stage1"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.synthesia.stage1"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    // Intentionally NOT depending on lifecycle-runtime-compose:2.8.0 — its new
    // LocalLifecycleOwner / collectAsStateWithLifecycle need Compose UI 1.7+ to be
    // auto-provided. We're on Compose BOM 2024.06 (UI 1.6.7) and use the Activity
    // directly as the LifecycleOwner.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Pitch detection is implemented in-tree (McLeod Pitch Method) — see PitchDetector.kt.
    // No external DSP dependency, which keeps the Docker build hermetic.

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
