package com.mustafakoceerr.tilepdf.language

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.mustafakoceerr.tilepdf.data.preferences.AppLanguageRepository
import com.mustafakoceerr.tilepdf.model.AppLanguage
import javax.inject.Inject

/**
 * Per-app language through AppCompat: the system keeps it on Android 13+,
 * AppCompat's metadata service (see the manifest) on older versions.
 */
class AppCompatLanguageRepository @Inject constructor() : AppLanguageRepository {

    override fun current(): AppLanguage =
        AppLanguage.fromTag(AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag())

    override fun set(language: AppLanguage) {
        val locales = language.tag?.let(LocaleListCompat::forLanguageTags) ?: LocaleListCompat.getEmptyLocaleList()
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
