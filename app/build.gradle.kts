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
        versionCode = 8
        versionName = "Most Latest · Developer Controls"
    }

    // The app's persistent release key, supplied from outside the repository: the CI workflow
    // decodes it from repository secrets into GARBY_KEYSTORE_FILE. It is a PKCS12 keystore whose
    // password also opens the key; the key's alias is "garby-release". Every release
    // must carry this key, or phones cannot update the app in place. Without it the release
    // build stays unsigned (no key is ever made up for it); only the local benchmark build
    // below falls back to this machine's debug key, for device checks.
    val garbyKeystore: String? = System.getenv("GARBY_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }
    signingConfigs {
        if (garbyKeystore != null) {
            create("garby") {
                storeFile = file(garbyKeystore)
                storeType = "pkcs12"
                storePassword = System.getenv("GARBY_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("GARBY_KEY_ALIAS") ?: "garby-release"
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
            if (garbyKeystore != null) signingConfig = signingConfigs.getByName("garby")
        }
        create("benchmark") {
            // Installable, release-equivalent local performance build: the release key when it
            // is supplied, otherwise this machine's debug key, for device checks only. The
            // published APK is the release build from the CI workflow, never this one.
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
