package com.mustafakoceerr.tilepdf.feature.settings

import com.mustafakoceerr.languagemanager.LanguageMode
import com.mustafakoceerr.tilepdf.model.AppLanguage

/** By language only, so "tr-TR" is Turkish; a language TilePDF doesn't offer follows the device. */
internal fun LanguageMode.toAppLanguage(): AppLanguage = when (this) {
    LanguageMode.SystemDefault -> AppLanguage.System
    is LanguageMode.Custom -> AppLanguage.fromTag(languageTag)
}

internal fun AppLanguage.toLanguageMode(): LanguageMode =
    tag?.let(LanguageMode::Custom) ?: LanguageMode.SystemDefault
