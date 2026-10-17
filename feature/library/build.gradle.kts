plugins {
    alias(libs.plugins.tilepdf.android.feature)
}

dependencies {
    implementation(projects.core.common)
    implementation(libs.androidx.activity.compose)
}
