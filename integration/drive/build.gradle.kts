plugins {
    id("meldo.android.feature")
    alias(libs.plugins.kotlin.serialization)
}

// Google Drive: the encrypted backup and sync between the student's devices.
android {
    namespace = "app.meldeplanner.android.integration.drive"
}

dependencies {
    // Google sign-in on Android: Google only allows its on-device authorization for Android clients.
    implementation(libs.play.services.auth)
}
