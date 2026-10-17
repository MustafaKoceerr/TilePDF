package com.mustafakoceerr.tilepdf.model

/** The app's language; [System] follows the device. [tag] is a BCP 47 language tag. */
enum class AppLanguage(val tag: String?) {
    System(null),
    English("en"),
    Turkish("tr"),
    ;

    companion object {
        /** By primary language only, so "tr-TR" is Turkish; anything else follows the device. */
        fun fromTag(tag: String?): AppLanguage {
            val language = tag?.substringBefore('-')?.lowercase()
            return entries.firstOrNull { it.tag != null && it.tag == language } ?: System
        }
    }
}
