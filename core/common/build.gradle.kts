plugins {
    alias(libs.plugins.tilepdf.android.library)
    alias(libs.plugins.tilepdf.hilt)
}

dependencies {
    api(libs.kotlinx.coroutines.android)
}
