plugins {
    alias(libs.plugins.tilepdf.android.library)
    alias(libs.plugins.tilepdf.hilt)
    id("org.jetbrains.kotlin.plugin.serialization")
}

dependencies {
    api(projects.core.model)
    api(projects.core.pdf)
    implementation(projects.core.common)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    androidTestImplementation(projects.core.testing)
    androidTestImplementation(libs.androidx.room.runtime)
    androidTestImplementation(libs.androidx.work.testing)
}
