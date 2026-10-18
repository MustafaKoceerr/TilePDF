plugins {
    alias(libs.plugins.tilepdf.android.library)
    alias(libs.plugins.tilepdf.hilt)
}

dependencies {
    api(projects.core.model)
    api(projects.core.pdf)
    implementation(projects.core.common)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(libs.androidx.core.ktx)

    androidTestImplementation(projects.core.testing)
}
