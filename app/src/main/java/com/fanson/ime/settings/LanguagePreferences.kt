package com.fanson.ime.settings

import com.fanson.ime.data.LocalStore
import com.fanson.ime.engine.InputLanguage

/** Language choices only; editor-specific temporary overrides are never saved here. */
class LanguagePreferences(private val store: LocalStore) {
    val defaultLanguage: InputLanguage
        get() = InputLanguage.fromPreference(store.get("default_language", "wubi86"))

    val preferredLanguage: InputLanguage
        get() = InputLanguage.fromPreference(store.get("input_language", defaultLanguage.value))

    fun quickSwitchTarget(current: InputLanguage): InputLanguage {
        if (current != InputLanguage.ENGLISH) return InputLanguage.ENGLISH
        val fallback = defaultLanguage.takeUnless { it == InputLanguage.ENGLISH } ?: InputLanguage.WUBI
        val remembered = store.get("last_non_english_language", "")
        return InputLanguage.entries.firstOrNull {
            it != InputLanguage.ENGLISH && it.value == remembered
        } ?: fallback
    }

    fun recordSelection(previous: InputLanguage, selected: InputLanguage) {
        val nonEnglish = if (selected != InputLanguage.ENGLISH) selected else previous
        store.putAll(buildMap {
            put("input_language", selected.value)
            if (nonEnglish != InputLanguage.ENGLISH) put("last_non_english_language", nonEnglish.value)
        })
    }

    fun setDefaultLanguage(language: InputLanguage) {
        store.putAll(mapOf(
            "default_language" to language.value,
            "last_non_english_language" to (language.takeUnless { it == InputLanguage.ENGLISH } ?: InputLanguage.WUBI).value,
            "input_language" to language.value
        ))
    }
}
