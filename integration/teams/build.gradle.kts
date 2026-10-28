plugins {
    id("meldo.android.feature")
    alias(libs.plugins.kotlin.serialization)
}

// Microsoft Teams: assignments become homework. Sign-in runs in a Custom Tab.
android {
    namespace = "app.meldeplanner.android.integration.teams"
}

dependencies {
    implementation(libs.androidx.browser)
}
