plugins {
    alias(libs.plugins.tilepdf.android.library)
    alias(libs.plugins.tilepdf.android.room)
    alias(libs.plugins.tilepdf.hilt)
}

dependencies {
    api(projects.core.model)
}
