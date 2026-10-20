import com.mustafakoceerr.tilepdf.libs
import org.gradle.api.Plugin
import org.gradle.api.Project

/** A screen module: Compose, Hilt, type-safe navigation and the core layers it builds on. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("tilepdf.android.library.compose")
            pluginManager.apply("tilepdf.hilt")
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
            with(dependencies) {
                add("implementation", project(":core:analytics"))
                add("implementation", project(":core:model"))
                add("implementation", project(":core:data"))
                add("implementation", project(":core:domain"))
                add("implementation", project(":core:designsystem"))
                listOf(
                    "androidx-lifecycle-runtime-compose",
                    "androidx-lifecycle-viewmodel-compose",
                    "androidx-navigation-compose",
                    "androidx-hilt-navigation-compose",
                    "kotlinx-serialization-json",
                ).forEach { add("implementation", libs.findLibrary(it).get()) }
            }
        }
    }
}
