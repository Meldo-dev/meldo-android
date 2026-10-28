plugins {
    id("meldo.android.app")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.meldeplanner.android"

    defaultConfig {
        applicationId = "app.meldeplanner.android"
        versionCode = 2
        versionName = "0.9.0"
        // Only the ABIs the Rust core is built for.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        manifestPlaceholders["teamsRedirectScheme"] = "meldeplanner"
        // OAuth client IDs stay out of the repo: ~/.gradle/gradle.properties locally,
        // ORG_GRADLE_PROJECT_MELDO_* secrets in CI. Missing means empty (feature unavailable).
        fun clientID(name: String) = providers.gradleProperty(name).orNull.orEmpty()
        buildConfigField("String", "GOOGLE_CLIENT_ID", "\"${clientID("MELDO_GOOGLE_CLIENT_ID")}\"")
        buildConfigField("String", "TEAMS_CLIENT_ID", "\"${clientID("MELDO_TEAMS_CLIENT_ID")}\"")
    }

    // The permanent release key (its SHA-1 is registered for Google sign-in) lives outside
    // the repo: ~/.gradle/gradle.properties names it. Without it (CI), the debug key signs.
    val releaseStore = providers.gradleProperty("MELDO_RELEASE_STORE_FILE").orNull
    signingConfigs {
        if (releaseStore != null) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = providers.gradleProperty("MELDO_RELEASE_STORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("MELDO_RELEASE_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("MELDO_RELEASE_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName(if (releaseStore != null) "release" else "debug")
            // R8: drops unused library code (Compose, the extended icon set) and resources;
            // without it the APK is ~58 MB, almost all of it dex.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures { buildConfig = true }

    packaging { jniLibs { useLegacyPackaging = false } }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:state"))
    implementation(project(":core:ui"))
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.work.runtime)
    implementation(libs.androidx.browser)
    implementation(libs.play.services.auth)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    // Document scanner (edge detection, crop, PDF), the counterpart of VisionKit on iOS.
    implementation(libs.mlkit.document.scanner)
    implementation(libs.compose.material3.adaptive.navigation.suite)
}
