plugins {
    alias(libs.plugins.tilepdf.android.feature)
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.render)
    implementation(projects.core.ocr)
    implementation(libs.androidx.activity.compose)

    androidTestImplementation(libs.androidx.compose.ui.test.junit4.accessibility)
}
