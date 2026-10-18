plugins {
    alias(libs.plugins.tilepdf.android.application)
    alias(libs.plugins.tilepdf.android.application.compose)
    alias(libs.plugins.tilepdf.hilt)
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
    implementation(projects.feature.library)
    implementation(projects.feature.reader)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)

    androidTestImplementation(projects.core.testing)
}
