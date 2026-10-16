import com.mustafakoceerr.tilepdf.JAVA_VERSION
import com.mustafakoceerr.tilepdf.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/** Pure Kotlin module: no Android framework, tests run on the JVM. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            extensions.configure<JavaPluginExtension> {
                sourceCompatibility = JAVA_VERSION
                targetCompatibility = JAVA_VERSION
            }
            extensions.configure<KotlinJvmProjectExtension> {
                compilerOptions.jvmTarget.set(JvmTarget.fromTarget(JAVA_VERSION.toString()))
            }
            with(dependencies) {
                add("testImplementation", libs.findLibrary("junit").get())
                add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
            }
        }
    }
}
