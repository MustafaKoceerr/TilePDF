plugins {
    alias(libs.plugins.tilepdf.android.library)
    alias(libs.plugins.tilepdf.hilt)
    alias(libs.plugins.tilepdf.pdfium)
}

android {
    defaultConfig {
        // Apps shrinking with R8 keep what the C++ code reaches through JNI.
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
    implementation(libs.androidx.core.ktx)

    androidTestImplementation(projects.core.testing)
}
