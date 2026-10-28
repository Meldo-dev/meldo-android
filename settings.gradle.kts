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

// Features: one module each. They depend on the core modules only, never on each other.
include(
    ":feature:grades",
    ":feature:holidays",
    ":feature:participation",
    ":feature:statistics",
    ":feature:subjects",
    ":feature:tasks",
)

// Core modules. They live in core-android/, because core/ is the meldo-core submodule
// (the Rust workspace and the shared string catalog).
for (name in listOf("engine", "l10n", "model", "state", "ui")) {
    include(":core:$name")
    project(":core:$name").projectDir = file("core-android/$name")
}
project(":core").projectDir = file("core-android")
