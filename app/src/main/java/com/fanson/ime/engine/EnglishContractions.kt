package com.fanson.ime.engine

import java.util.Locale

/** Deliberate local shortcuts, not a learned dictionary or automatic correction.
 * Keep the exact supported list in docs/technical/ENGLISH_INPUT.md in sync. */
internal object EnglishContractions {
    private val forms = listOf(
        "I'm", "I'd", "I've", "it's", "you're", "you've", "you'll", "you'd",
        "he's", "she's", "that's", "there's", "what's", "who's", "let's",
        "don't", "doesn't", "didn't", "can't", "couldn't", "won't", "wouldn't",
        "shouldn't", "isn't", "aren't", "wasn't", "weren't", "haven't",
        "hasn't", "hadn't", "mustn't", "needn't"
    )
    private val shortcuts = forms.associateBy { it.replace("'", "").lowercase(Locale.ROOT) }
    fun shortcut(prefix: String): String? = shortcuts[prefix.lowercase(Locale.ROOT)]
    fun explicit(prefix: String): List<String> {
        val lower = prefix.lowercase(Locale.ROOT)
        return forms.filter { it.lowercase(Locale.ROOT).startsWith(lower) }
    }
    fun cased(word: String, prefix: String): String = when {
        prefix.all { !it.isLetter() || it.isUpperCase() } -> word.uppercase(Locale.ROOT)
        word.startsWith("I'") -> word
        prefix.first().isUpperCase() -> word.replaceFirstChar { it.uppercaseChar() }
        else -> word
    }
}
