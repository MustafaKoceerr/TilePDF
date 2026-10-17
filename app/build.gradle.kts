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
        versionName = "1.0.0"
    }

    androidResources {
        generateLocaleConfig = true
        // Only our languages; libraries' translations would list dozens more.
        localeFilters += listOf("en", "tr")
    }

    buildTypes {
        release {
            optimization {
                enable = true
                keepRules {
                    files.add(file("proguard-rules.pro"))
                }
            }
            // Local checks of the shrunk build only (-Ptilepdf.signReleaseWithDebug=true);
            // a build for Play is signed with the upload key.
            if (providers.gradleProperty("tilepdf.signReleaseWithDebug").orNull == "true") {
                signingConfig = signingConfigs.getByName("debug")
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
    implementation(projects.feature.settings)

    implementation(libs.androidx.activity.compose)
    // MainActivity is an AppCompatActivity: the language library needs one below Android 13.
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.theme.manager.hilt)
    implementation(libs.language.manager.hilt)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.work.runtime.ktx)

    androidTestImplementation(projects.core.testing)
}
