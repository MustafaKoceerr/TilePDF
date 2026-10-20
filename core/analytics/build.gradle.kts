plugins {
    alias(libs.plugins.tilepdf.android.library)
    alias(libs.plugins.tilepdf.hilt)
}

// Firebase stays behind this module's interfaces; the rest of the app never
// sees it. Without google-services.json Firebase isn't set up and the no-op
// versions are used.
dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics.ndk)
}
