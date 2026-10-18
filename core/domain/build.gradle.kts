plugins {
    alias(libs.plugins.tilepdf.android.library)
    alias(libs.plugins.tilepdf.hilt)
}

dependencies {
    api(projects.core.data)
    api(projects.core.model)
    api(projects.core.pdf)
}
