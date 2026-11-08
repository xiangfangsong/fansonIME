package com.fanson.ime.engine

import java.util.Locale

/** Read-only ranked vocabulary; binary prefix range, frequency ordering within it. */
class EnglishLexicon(rankedWords: List<String>) {
    private val ranks = rankedWords.withIndex().associate { it.value to it.index }
    private val words = ranks.keys.sorted()

    fun lookup(prefix: String): List<Candidate> {
        if (prefix.isEmpty()) return emptyList()
        val lower = prefix.lowercase(Locale.ROOT)
        fun bound(key: String): Int {
            val result = words.binarySearch(key)
            return if (result >= 0) result else -result - 1
        }
        return words.subList(bound(lower), bound(lower + '{'))
            .sortedWith(compareBy<String> { it != lower }.thenBy { ranks[it] })
            .take(80).map { word ->
                val cased = when {
                    prefix.all { it.isUpperCase() } -> word.uppercase(Locale.ROOT)
                    prefix.first().isUpperCase() -> word.replaceFirstChar { it.uppercaseChar() }
                    else -> word
                }
                Candidate(cased, "")
            }
    }
}

/** English preedit is shown with setComposingText, never saved or learned. */
class EnglishEngine(private val lexicon: EnglishLexicon) : InputEngine {
    override var code = ""
        private set
    override var candidates = emptyList<Candidate>()
        private set
    var enabled = true

    override fun reset() { code = ""; candidates = emptyList() }
    private fun refresh() { candidates = lexicon.lookup(code) }
    override fun letter(char: Char): EngineResult {
        if (!char.isLetter() || char.code > 127) return EngineResult()
        if (!enabled) return EngineResult(listOf(char.toString()))
        // Bound temporary preedit even for unusually long identifiers.
        val commits = if (code.length >= 64) resolve().commits else emptyList()
        code += char
        refresh()
        return EngineResult(commits, composing = code)
    }
    override fun select(index: Int): EngineResult {
        val selected = candidates.getOrNull(index) ?: return EngineResult()
        reset()
        return EngineResult(listOf(selected.text))
    }
    override fun resolve(): EngineResult {
        val raw = code
        reset()
        return EngineResult(if (raw.isEmpty()) emptyList() else listOf(raw))
    }
    override fun enter() = resolve()
    override fun space() = punctuation(" ")
    override fun punctuation(text: String): EngineResult = resolve().let { it.copy(commits = it.commits + text) }
    override fun backspace(): Boolean {
        if (code.isEmpty()) return false
        code = code.dropLast(1)
        refresh()
        return true
    }
}
