plugins {
    alias(libs.plugins.tilepdf.android.application)
    alias(libs.plugins.tilepdf.android.application.compose)
    alias(libs.plugins.tilepdf.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.mustafakoceerr.tilepdf"

    defaultConfig {
        applicationId = "com.mustafakoceerr.tilepdf"
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(projects.core.domain)
    implementation(projects.core.model)
    implementation(projects.core.pdf)
    implementation(projects.core.render)

    androidTestImplementation(projects.core.testing)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
}
