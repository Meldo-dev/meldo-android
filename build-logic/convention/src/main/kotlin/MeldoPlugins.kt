import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

private val Project.libs: VersionCatalog get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

private fun VersionCatalog.lib(alias: String) = findLibrary(alias).get()

/** Settings every module shares: SDK levels and Java 21. */
private fun CommonExtension.meldoDefaults() {
    compileSdk = 37
    defaultConfig.minSdk = 26
    compileOptions.sourceCompatibility = JavaVersion.VERSION_21
    compileOptions.targetCompatibility = JavaVersion.VERSION_21
}

/** Compose for every module that draws UI; the libraries come with `:core:ui`. */
private fun Project.meldoCompose() {
    pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
    extensions.getByType(CommonExtension::class.java).buildFeatures.compose = true
    dependencies {
        add("implementation", platform(libs.lib("compose-bom")))
        add("debugImplementation", libs.lib("compose-ui-tooling"))
    }
}

/** A module without UI (`:core:model`, `:core:engine`, …). */
class MeldoLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        extensions.configure<LibraryExtension> { meldoDefaults() }
        dependencies { add("testImplementation", libs.lib("junit")) }
    }
}

/**
 * A feature, integration or other UI module: Compose plus the core modules. Features
 * depend only on core modules, never on each other; links between them go through
 * the feature registry in `:core:ui`.
 */
class MeldoFeaturePlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("meldo.android.library")
        meldoCompose()
        dependencies {
            add("implementation", project(":core:model"))
            add("implementation", project(":core:state"))
            add("implementation", project(":core:ui"))
        }
    }
}

/** The app: composition root with the feature list. */
class MeldoAppPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        extensions.configure<ApplicationExtension> {
            meldoDefaults()
            defaultConfig.targetSdk = 37
        }
        meldoCompose()
        dependencies { add("testImplementation", libs.lib("junit")) }
    }
}
