pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Meldo"

include(":app")

// Core modules. They live in core-android/, because core/ is the meldo-core submodule
// (the Rust workspace and the shared string catalog).
for (name in listOf("engine", "l10n", "model", "state", "ui")) {
    include(":core:$name")
    project(":core:$name").projectDir = file("core-android/$name")
}
project(":core").projectDir = file("core-android")
