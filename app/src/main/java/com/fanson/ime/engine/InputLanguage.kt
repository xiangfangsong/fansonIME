package com.fanson.ime.engine

/** Declaration order is also the permanent language-menu order. */
enum class InputLanguage(val value: String, val label: String) {
    ENGLISH("english", "English"),
    WUBI("wubi86", "五笔86"),
    PINYIN("pinyin", "全拼26键"),
    KOREAN("korean", "한국어");

    companion object {
        fun fromPreference(value: String) = entries.firstOrNull { it.value == value } ?: WUBI
    }
}
