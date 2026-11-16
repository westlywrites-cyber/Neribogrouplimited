plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The signing key comes from GitHub secrets (the workflow decodes it to a file).
// Using the same key for every build is what lets a new APK install over the old app.
val keystorePath: String? = System.getenv("KEYSTORE_PATH")

android {
    namespace = "com.westly.neribogroup"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.westly.neribogroup"
        minSdk = 26
        targetSdk = 34
        // Goes up by one on every GitHub build, so Android accepts it as an update.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "1.0"
    }

    // Three apps from this one project. "neribo" is the normal Neribo Group app (it has no Storage room and no Dell
    // office). "nco" is NCO (Neribo CEO Office): its own app that opens straight on the Dell desktop. "room" is
    // Storage Room: its own app that opens straight on the Storage room (fingerprint first, then the PIN).
    // All three use the same cloud (Supabase) and are signed with the same key.
    flavorDimensions += "app"
    productFlavors {
        create("neribo") {
            dimension = "app"
            buildConfigField("boolean", "CEO_ONLY", "false")
            buildConfigField("boolean", "ROOM_ONLY", "false")
        }
        create("nco") {
            dimension = "app"
            applicationIdSuffix = ".ceo"
            buildConfigField("boolean", "CEO_ONLY", "true")
            buildConfigField("boolean", "ROOM_ONLY", "false")
        }
        create("room") {
            dimension = "app"
            applicationIdSuffix = ".room"
            buildConfigField("boolean", "CEO_ONLY", "false")
            buildConfigField("boolean", "ROOM_ONLY", "true")
        }
    }

    signingConfigs {
        if (keystorePath != null && File(keystorePath).exists()) {
            create("release") {
                storeFile = File(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    lint {
        checkReleaseBuilds = false
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
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")

    // Storage room: a WebSocket for Supabase Realtime (the live feed from the owner's other phones).
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")

    // Biometric verification. BiometricPrompt needs a FragmentActivity; its fallback dialog on Android 8 needs an
    // AppCompat theme. Versions are set here so they match the activity and lifecycle versions above.
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment:1.8.3")
    implementation("androidx.appcompat:appcompat:1.7.0")

    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    // The demo OPay app, shown inside the Dell desktop. Only NCO has it.
    "ncoImplementation"(project(":opay"))
}
