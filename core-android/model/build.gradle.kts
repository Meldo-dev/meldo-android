plugins {
    id("meldo.android.library")
    alias(libs.plugins.kotlin.serialization)
}

// The data everything shares: AppData and its JSON (the sync format), local storage,
// secrets, the Rust engine mapping, the calendar and L(…).
android {
    namespace = "app.meldeplanner.android.model"
}

dependencies {
    api(project(":core:engine"))
    api(project(":core:l10n"))
    api(libs.kotlinx.serialization.json)
}
