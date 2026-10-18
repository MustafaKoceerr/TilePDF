package com.mustafakoceerr.tilepdf

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

const val COMPILE_SDK = 37
const val MIN_SDK = 29
const val TARGET_SDK = 37
val JAVA_VERSION = JavaVersion.VERSION_11

val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

/** SDK levels and Java version shared by the app and every Android library. */
internal fun configureKotlinAndroid(extension: CommonExtension) = with(extension) {
    compileSdk = COMPILE_SDK
    defaultConfig.minSdk = MIN_SDK
    defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    compileOptions.sourceCompatibility = JAVA_VERSION
    compileOptions.targetCompatibility = JAVA_VERSION
}

/** Compose compiler plus the BOM-managed UI libraries every Compose module uses. */
internal fun Project.configureAndroidCompose(extension: CommonExtension) {
    pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
    extension.buildFeatures.compose = true
    with(dependencies) {
        val bom = platform(libs.findLibrary("androidx-compose-bom").get())
        add("implementation", bom)
        add("androidTestImplementation", bom)
        add("implementation", libs.findLibrary("androidx-compose-ui").get())
        add("implementation", libs.findLibrary("androidx-compose-ui-graphics").get())
        add("implementation", libs.findLibrary("androidx-compose-ui-tooling-preview").get())
        add("implementation", libs.findLibrary("androidx-compose-material3").get())
        add("debugImplementation", libs.findLibrary("androidx-compose-ui-tooling").get())
        add("debugImplementation", libs.findLibrary("androidx-compose-ui-test-manifest").get())
        add("androidTestImplementation", libs.findLibrary("androidx-compose-ui-test-junit4").get())
    }
}

/** Test libraries shared by the app and every Android library. */
internal fun Project.addAndroidTestDependencies() = with(dependencies) {
    add("testImplementation", libs.findLibrary("junit").get())
    add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
    add("testImplementation", libs.findLibrary("turbine").get())
    add("androidTestImplementation", libs.findLibrary("androidx-junit").get())
    add("androidTestImplementation", libs.findLibrary("androidx-espresso-core").get())
}
