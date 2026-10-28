plugins {
    id("meldo.android.feature")
    alias(libs.plugins.kotlin.serialization)
}

// Everything outside the app that shows Meldo data: home screen widgets (Glance), the
// Quick Settings tile, after-lesson check-ins, task reminders, the live lesson
// notification and background refresh. The app's manifest declares the components.
android {
    namespace = "app.meldeplanner.android.surfaces"
}

dependencies {
    // api: the app manifest declares the widget receivers, and lint checks their Glance superclass.
    api(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.work.runtime)
}
