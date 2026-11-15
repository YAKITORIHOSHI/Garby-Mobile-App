plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.googleServices)
}

android {
    namespace = "com.thesis.garby"
    // Android 17 QPR2 SDK (37.2). Compose 1.12 and core 1.19 need at least API 37.
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "com.thesis.garby"
        minSdk = 26
        // Android 17 target-only changes (local network permission, certificate transparency,
        // background audio, widgets, RFCOMM reads) do not touch this Firebase-only app.
        targetSdk = 37
        versionCode = 7
        versionName = "Most Latest · Phone Control"
    }

    // The release build's own key, supplied from outside the repository (the CI workflow
    // reads it from repository secrets). Without it the benchmark build falls back to the
    // machine's debug key, which installs but differs from machine to machine.
    val garbyKeystore: String? = System.getenv("GARBY_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }
    signingConfigs {
        if (garbyKeystore != null) {
            create("garby") {
                storeFile = file(garbyKeystore)
                storeType = "pkcs12"
                storePassword = System.getenv("GARBY_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("GARBY_KEY_ALIAS") ?: "garby"
                keyPassword = System.getenv("GARBY_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // No app-specific keep rules are needed; each library ships its own consumer rules.
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
        create("benchmark") {
            // Installable, release-equivalent local performance build. Production release
            // signing stays external; the development key is only for device verification.
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName(if (garbyKeystore != null) "garby" else "debug")
            isDebuggable = false
            matchingFallbacks += "release"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt"
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.database)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.installations)
    implementation(libs.kotlinx.coroutines.play.services)

    debugImplementation(libs.androidx.ui.tooling)

    constraints {
        // Play services brings an old Fragment; the Activity Result API needs 1.3.0 or later.
        implementation(libs.androidx.fragment)
    }

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
