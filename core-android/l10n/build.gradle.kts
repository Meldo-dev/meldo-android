plugins {
    id("meldo.android.library")
}

android {
    namespace = "app.meldeplanner.android.l10n"
}

// One catalog for both apps: meldo-core's String Catalog (German keys, de/en) plus the
// few Android-only strings, merged into assets/strings.json for L(…).
val generateStrings = tasks.register("generateStrings") {
    group = "meldo"
    val catalog = rootProject.file("core/l10n/Localizable.xcstrings")
    // Android-only strings, one file per feature: strings/<feature>.json = { key: { de, en } }.
    val androidOnly = rootProject.fileTree("strings") { include("*.json") }
    val output = layout.buildDirectory.file("generated/strings/assets/strings.json")
    inputs.file(catalog)
    inputs.files(androidOnly)
    outputs.file(output)
    doLast {
        @Suppress("UNCHECKED_CAST")
        val strings = (groovy.json.JsonSlurper().parse(catalog) as Map<String, Any>)["strings"] as Map<String, Map<String, Any>>
        val merged = sortedMapOf<String, List<String>>()
        for ((key, entry) in strings) {
            @Suppress("UNCHECKED_CAST")
            val locs = entry["localizations"] as? Map<String, Map<String, Map<String, String>>> ?: emptyMap()
            merged[key] = listOf("de", "en").map { locs[it]?.get("stringUnit")?.get("value") ?: key }
        }
        for (file in androidOnly.files.sortedBy { it.name }) {
            @Suppress("UNCHECKED_CAST")
            val extra = groovy.json.JsonSlurper().parse(file) as Map<String, Map<String, String>>
            for ((key, values) in extra) merged[key] = listOf("de", "en").map { values[it] ?: key }
        }
        val file = output.get().asFile
        file.parentFile.mkdirs()
        file.writeText(groovy.json.JsonOutput.toJson(merged), Charsets.UTF_8)
    }
}

extensions.configure<com.android.build.api.dsl.LibraryExtension> {
    sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/strings/assets").get().asFile)
}
tasks.named("preBuild") { dependsOn(generateStrings) }
