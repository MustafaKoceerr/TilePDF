import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.mustafakoceerr.tilepdf.CheckReleasePermissionsTask
import com.mustafakoceerr.tilepdf.TARGET_SDK
import com.mustafakoceerr.tilepdf.addAndroidTestDependencies
import com.mustafakoceerr.tilepdf.configureKotlinAndroid
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)
                defaultConfig.targetSdk = TARGET_SDK
            }
            addAndroidTestDependencies()
            checkReleasePermissions()
        }
    }

    /** The release never ships a permission outside the allowlist; TilePDF stays offline. */
    private fun Project.checkReleasePermissions() {
        extensions.configure<ApplicationAndroidComponentsExtension> {
            onVariants(selector().withBuildType("release")) { variant ->
                val check = tasks.register<CheckReleasePermissionsTask>("checkReleasePermissions") {
                    mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
                }
                tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }
                    .configureEach { dependsOn(check) }
            }
        }
    }
}
