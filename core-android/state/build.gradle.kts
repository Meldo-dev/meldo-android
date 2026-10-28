plugins {
    id("meldo.android.library")
}

// AppModel: the one state object every screen reads, its persistence (mutate) and the
// shared queries over the data (week, tasks, exams, grades, holidays).
android {
    namespace = "app.meldeplanner.android.state"
}

dependencies {
    api(project(":core:model"))
    api(platform(libs.compose.bom))
    api("androidx.compose.runtime:runtime")
    api(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core)
}
