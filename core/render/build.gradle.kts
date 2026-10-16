plugins {
    alias(libs.plugins.tilepdf.android.library.compose)
    alias(libs.plugins.tilepdf.hilt)
}

dependencies {
    api(projects.core.pdf)
    implementation(projects.core.common)
    implementation(libs.androidx.core.ktx)

    androidTestImplementation(projects.core.testing)
}
