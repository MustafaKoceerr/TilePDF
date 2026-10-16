plugins {
    alias(libs.plugins.tilepdf.android.library)
}

// Test fixtures (PDFs in assets, TestAssets) for instrumented tests; consumed
// only through androidTestImplementation.
dependencies {
    api(projects.core.pdf)
    api(libs.androidx.test.core)
    api(libs.androidx.test.runner)
    api(libs.kotlinx.coroutines.android)
}
