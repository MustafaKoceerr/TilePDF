import com.mustafakoceerr.tilepdf.ReleaseSigning
import com.mustafakoceerr.tilepdf.releaseSigning

plugins {
    alias(libs.plugins.tilepdf.android.application)
    alias(libs.plugins.tilepdf.android.application.compose)
    alias(libs.plugins.tilepdf.hilt)
    alias(libs.plugins.aboutlibraries.android)
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

    // The Play upload key comes only from the environment; see docs/RELEASING.md.
    val signing = releaseSigning { name -> providers.environmentVariable(name).orNull }
    when (signing) {
        is ReleaseSigning.Configured -> signingConfigs.create("release") {
            storeFile = file(signing.storeFile)
            storePassword = signing.storePassword
            keyAlias = signing.keyAlias
            keyPassword = signing.keyPassword
        }
        is ReleaseSigning.Incomplete -> throw GradleException(signing.message)
        ReleaseSigning.Unsigned -> logger.warn("TilePDF: no upload key in the environment; release builds are unsigned.")
    }

    buildTypes {
        release {
            optimization {
                enable = true
                keepRules {
                    files.add(file("proguard-rules.pro"))
                }
            }
            signingConfig = when {
                signing is ReleaseSigning.Configured -> signingConfigs.getByName("release")
                // Local checks of the shrunk build only (-Ptilepdf.signReleaseWithDebug=true);
                // never in place of the upload key.
                providers.gradleProperty("tilepdf.signReleaseWithDebug").orNull == "true" -> signingConfigs.getByName("debug")
                else -> null
            }
        }
    }
}

// Licenses of every bundled library, shown on the About screen. The build may
// fetch standard license texts (e.g. Apache-2.0) from SPDX when a dependency's
// metadata names the license without its text; the app itself never goes online.
aboutLibraries {
    offlineMode = false
    collect {
        filterVariants.add("release")
        // The developer's own JitPack libraries publish no license in their POMs.
        configPath = file("../config/aboutlibraries")
    }
    export {
        excludeFields.addAll("developers", "funding")
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(projects.core.domain)
    implementation(projects.feature.about)
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
