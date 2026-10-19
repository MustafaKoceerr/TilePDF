plugins {
    alias(libs.plugins.tilepdf.android.library)
    alias(libs.plugins.tilepdf.hilt)
}

android {
    defaultConfig {
        // Apps shrinking with R8 keep what ML Kit creates by reflection.
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    api(projects.core.model)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.kotlinx.coroutines.android)

    androidTestImplementation(projects.core.pdf)
    androidTestImplementation(projects.core.testing)
}
