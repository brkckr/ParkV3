package com.brkckr.parkv3.ui.language

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** Languages the app ships; [SYSTEM] follows the device setting (docs/adr/0013). */
enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    TURKISH("tr"),
    ENGLISH("en"),
    ;

    companion object {
        /** Stored locale tags ("tr-TR", "en", "") to a language; anything else is the system default. */
        fun fromTags(tags: String): AppLanguage {
            val language = tags.substringBefore(',').substringBefore('-').trim().lowercase()
            return entries.firstOrNull { it != SYSTEM && it.tag == language } ?: SYSTEM
        }

        fun current(): AppLanguage = fromTags(AppCompatDelegate.getApplicationLocales().toLanguageTags())

        /**
         * Android 13+ stores the choice in the system per-app language setting; on older
         * versions AppCompat stores it. The activity is recreated in the new language.
         */
        fun select(language: AppLanguage) {
            AppCompatDelegate.setApplicationLocales(
                if (language == SYSTEM) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(language.tag),
            )
        }
    }
}
