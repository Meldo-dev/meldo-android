import java.util.Properties

plugins {
    id("meldo.android.library")
}

// The Rust core (meldo-core, the core/ submodule): libmelde_ffi.so for each ABI and the
// generated Kotlin bindings, the same way meldo-ios builds them for Swift.
val repoRoot: File = rootProject.file("core")
val rustJniLibs = layout.buildDirectory.dir("rust/jniLibs")
val uniffiBindings = layout.buildDirectory.dir("generated/uniffi")
val rustProfile = "release"
val ndk = "29.0.14206865"

extensions.configure<com.android.build.api.dsl.LibraryExtension> {
    namespace = "app.meldeplanner.android.engine"
    ndkVersion = ndk

    sourceSets {
        getByName("main") {
            jniLibs.srcDir(rustJniLibs.get().asFile)
            kotlin.srcDir(uniffiBindings.get().asFile)
        }
    }
}

dependencies {
    // UniFFI's Kotlin bindings call the Rust library through JNA.
    api("${libs.jna.get()}@aar")
}

fun sdkDir(): File {
    val props = Properties()
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(props::load)
    val path = props.getProperty("sdk.dir") ?: System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
    return File(requireNotNull(path) { "Set sdk.dir in local.properties or ANDROID_HOME" })
}

val cargoBuildRust = tasks.register<Exec>("cargoBuildRust") {
    group = "rust"
    description = "Builds the Rust core for Android with cargo-ndk."
    workingDir = repoRoot
    // NDK r29 ships sysroots up to API 35; the library uses no newer native API.
    environment("ANDROID_NDK_HOME", sdkDir().resolve("ndk/$ndk").path)
    commandLine(
        "cargo", "ndk", "-t", "arm64-v8a", "-t", "x86_64", "--platform", "35",
        "-o", rustJniLibs.get().asFile.path,
        "build", "-p", "melde-ffi", "--locked", "--$rustProfile",
    )
    inputs.dir(repoRoot.resolve("crates"))
    inputs.file(repoRoot.resolve("Cargo.lock"))
    outputs.dir(rustJniLibs)
}

// Release libraries are stripped, so bindgen reads the UniFFI metadata from a host build.
val cargoBuildHost = tasks.register<Exec>("cargoBuildHost") {
    group = "rust"
    workingDir = repoRoot
    commandLine("cargo", "build", "-q", "-p", "melde-ffi", "--locked")
    inputs.dir(repoRoot.resolve("crates"))
    outputs.dir(repoRoot.resolve("target/debug"))
}

val generateUniffiBindings = tasks.register<Exec>("generateUniffiBindings") {
    group = "rust"
    description = "Generates the Kotlin bindings for the Rust core."
    dependsOn(cargoBuildRust, cargoBuildHost)
    workingDir = repoRoot
    val os = System.getProperty("os.name").lowercase()
    val hostLib = when {
        "windows" in os -> "melde_ffi.dll"
        "mac" in os -> "libmelde_ffi.dylib"
        else -> "libmelde_ffi.so"
    }
    val library = repoRoot.resolve("target/debug/$hostLib")
    commandLine(
        "cargo", "run", "-q", "-p", "uniffi-bindgen", "--locked", "--",
        "generate", "--library", library.path, "--language", "kotlin", "--no-format",
        "--out-dir", uniffiBindings.get().asFile.path,
    )
    inputs.dir(repoRoot.resolve("crates"))
    outputs.dir(uniffiBindings)
}

tasks.named("preBuild") { dependsOn(generateUniffiBindings) }
