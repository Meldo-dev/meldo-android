plugins {
    id("meldo.android.library")
    alias(libs.plugins.kotlin.compose)
}

// The design system (theme, components, form rows, shared rows), the navigation
// scaffold and the feature registry.
android {
    namespace = "app.meldeplanner.android.ui"
    buildFeatures { compose = true }
}

dependencies {
    api(project(":core:state"))
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.ui.tooling.preview)
    api(libs.compose.material3)
    api(libs.compose.material.icons)
    api(libs.androidx.activity.compose)
    api(libs.androidx.lifecycle.runtime)
    api(libs.androidx.core)
    // Translucent, blurred app bars and navigation (backdrop blur), the counterpart of iOS
    // materials; Compose has no built-in backdrop blur.
    api(libs.haze)
    api(libs.haze.blur)
    api(libs.haze.blur.materials)
    debugImplementation(libs.compose.ui.tooling)
}
