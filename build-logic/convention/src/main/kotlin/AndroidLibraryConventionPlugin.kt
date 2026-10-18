import com.android.build.api.dsl.LibraryExtension
import com.mustafakoceerr.tilepdf.addAndroidTestDependencies
import com.mustafakoceerr.tilepdf.configureKotlinAndroid
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
                // Follows the module path: :core:render -> com.mustafakoceerr.tilepdf.core.render
                namespace = "com.mustafakoceerr.tilepdf" + path.replace(':', '.')
            }
            addAndroidTestDependencies()
        }
    }
}
