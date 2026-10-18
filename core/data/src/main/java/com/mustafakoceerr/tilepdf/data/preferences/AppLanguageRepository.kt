package com.mustafakoceerr.tilepdf.data.preferences

import com.mustafakoceerr.tilepdf.model.AppLanguage

/** The language chosen inside the app; the platform stores it. */
interface AppLanguageRepository {
    fun current(): AppLanguage

    /** Applies [language] at once; the activity is recreated in it. Main thread only. */
    fun set(language: AppLanguage)
}
