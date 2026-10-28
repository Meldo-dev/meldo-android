plugins {
    id("meldo.android.app")
}

// The composition root: MainActivity, MeldoApp, the feature list (MeldoFeatures.kt) and
// RootScreen, plus the manifest components, the OAuth client IDs, signing and R8.

android {
    namespace = "app.meldeplanner.android"

    defaultConfig {
        applicationId = "app.meldeplanner.android"
        versionCode = 2
        versionName = "0.9.0"
        // Only the ABIs the Rust core is built for.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        manifestPlaceholders["teamsRedirectScheme"] = "meldeplanner"
        manifestPlaceholders["appLabel"] = "Meldo"
        // The demo build type (below) starts with sample data instead of the student's own.
        buildConfigField("Boolean", "DEMO", "false")
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
        // "Meldo Demo" for store screenshots and the website: its own app ID, so it installs
        // next to the real app; a sample school week in memory, nothing saved, no widgets,
        // reminders or background updates. Never shipped.
        create("demo") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".demo"
            manifestPlaceholders["appLabel"] = "Meldo Demo"
            buildConfigField("Boolean", "DEMO", "true")
            matchingFallbacks += listOf("debug")
        }
    }

    buildFeatures { buildConfig = true }

    packaging { jniLibs { useLegacyPackaging = false } }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:state"))
    implementation(project(":core:ui"))

    // The features and connected services in this build; MeldoFeatures.kt lists the same ones.
    implementation(project(":feature:calendar"))
    implementation(project(":feature:dashboard"))
    implementation(project(":feature:exams"))
    implementation(project(":feature:grades"))
    implementation(project(":feature:holidays"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:participation"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:statistics"))
    implementation(project(":feature:subjects"))
    implementation(project(":feature:tasks"))
    implementation(project(":feature:timetable"))
    implementation(project(":integration:untis"))
    implementation(project(":integration:teams"))
    implementation(project(":integration:drive"))
    implementation(project(":surfaces"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.compose.material3.adaptive.navigation.suite)
}
