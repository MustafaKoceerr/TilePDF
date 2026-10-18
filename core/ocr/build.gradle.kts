plugins {
    alias(libs.plugins.tilepdf.android.library)
    alias(libs.plugins.tilepdf.hilt)
}

dependencies {
    api(projects.core.model)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.kotlinx.coroutines.android)
}
