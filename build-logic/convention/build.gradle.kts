plugins {
    `kotlin-dsl`
}

dependencies {
    // Only for compiling the plugins; the build itself applies the versions from the catalog.
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApp") {
            id = "meldo.android.app"
            implementationClass = "MeldoAppPlugin"
        }
        register("androidLibrary") {
            id = "meldo.android.library"
            implementationClass = "MeldoLibraryPlugin"
        }
        register("androidFeature") {
            id = "meldo.android.feature"
            implementationClass = "MeldoFeaturePlugin"
        }
    }
}
