plugins {
    alias(libs.plugins.tilepdf.android.feature)
}

dependencies {
    implementation(libs.theme.manager)
    implementation(libs.language.manager)

    testImplementation(libs.theme.manager.testing)
    testImplementation(libs.language.manager.testing)

    androidTestImplementation(libs.androidx.compose.ui.test.junit4.accessibility)
}
