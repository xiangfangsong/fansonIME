package com.fanson.ime.engine

/** Modern PC two-set Hangul. Only the active syllable and its key history are retained. */
class KoreanEngine : InputEngine {
    private data class Syllable(val initial: Int = -1, val vowel: Int = -1, val final: Int = 0) {
        fun text(): String = when {
            initial >= 0 && vowel >= 0 -> (0xAC00 + (initial * 21 + vowel) * 28 + final).toChar().toString()
            initial >= 0 -> INITIALS[initial].toString()
            vowel >= 0 -> VOWELS[vowel].toString()
            else -> ""
        }
    }

    private var syllable = Syllable()
    private val history = mutableListOf<Syllable>()
    override val code: String get() = syllable.text()
    override val candidates: List<Candidate> get() = emptyList()
    override fun reset() { syllable = Syllable(); history.clear() }

    override fun letter(char: Char): EngineResult {
        val jamo = keyLabel(char) ?: return EngineResult()
        val nextInitial = INITIALS.indexOf(jamo)
        val nextVowel = VOWELS.indexOf(jamo)
        val old = syllable
        var committed = ""
        fun change(next: Syllable) { history += old; syllable = next }
        fun start(next: Syllable) {
            committed = old.text()
            history.clear(); history += Syllable(); syllable = next
        }
        when {
            code.isEmpty() -> change(Syllable(nextInitial, nextVowel))
            old.vowel < 0 -> {
                if (nextVowel >= 0) change(old.copy(vowel = nextVowel))
                else start(Syllable(initial = nextInitial))
            }
            old.initial < 0 -> {
                val combined = VOWEL_PAIRS[old.vowel to nextVowel]
                if (combined != null) change(old.copy(vowel = combined))
                else start(Syllable(nextInitial, nextVowel))
            }
            old.final == 0 -> {
                if (nextVowel >= 0) {
                    val combined = VOWEL_PAIRS[old.vowel to nextVowel]
                    if (combined != null) change(old.copy(vowel = combined))
                    else start(Syllable(vowel = nextVowel))
                } else {
                    val final = FINALS.indexOf(jamo)
                    if (final > 0) change(old.copy(final = final))
                    else start(Syllable(initial = nextInitial))
                }
            }
            nextVowel >= 0 -> {
                val split = FINAL_SPLITS[old.final]
                val retained = split?.first ?: 0
                val moved = split?.second ?: old.final
                committed = old.copy(final = retained).text()
                val initial = INITIALS.indexOf(FINALS[moved])
                history.clear()
                history += Syllable()
                history += Syllable(initial = initial)
                syllable = Syllable(initial, nextVowel)
            }
            else -> {
                val nextFinal = FINALS.indexOf(jamo)
                val combined = FINAL_PAIRS[old.final to nextFinal]
                if (combined != null) change(old.copy(final = combined))
                else start(Syllable(initial = nextInitial))
            }
        }
        return EngineResult(if (committed.isEmpty()) emptyList() else listOf(committed), composing = code)
    }

    override fun backspace(): Boolean {
        if (code.isEmpty()) return false
        syllable = if (history.isEmpty()) Syllable() else history.removeAt(history.lastIndex)
        return true
    }
    override fun select(index: Int) = EngineResult()
    override fun resolve(): EngineResult {
        val text = code
        reset()
        return EngineResult(if (text.isEmpty()) emptyList() else listOf(text))
    }
    override fun enter() = resolve()
    override fun space() = punctuation(" ")
    override fun punctuation(text: String) = resolve().let { it.copy(commits = it.commits + text) }

    companion object {
        private const val INITIALS = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
        private const val VOWELS = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
        private const val FINALS = " ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ"
        private const val KEYS = "abcdefghijklmnopqrstuvwxyz"
        private const val JAMO = "ㅁㅠㅊㅇㄷㄹㅎㅗㅑㅓㅏㅣㅡㅜㅐㅔㅂㄱㄴㅅㅕㅍㅈㅌㅛㅋ"
        private val SHIFTED = mapOf('Q' to 'ㅃ', 'W' to 'ㅉ', 'E' to 'ㄸ', 'R' to 'ㄲ', 'T' to 'ㅆ', 'O' to 'ㅒ', 'P' to 'ㅖ')
        private val VOWEL_PAIRS = mapOf((8 to 0) to 9, (8 to 1) to 10, (8 to 20) to 11,
            (13 to 4) to 14, (13 to 5) to 15, (13 to 20) to 16, (18 to 20) to 19)
        private val FINAL_PAIRS = mapOf((1 to 19) to 3, (4 to 22) to 5, (4 to 27) to 6,
            (8 to 1) to 9, (8 to 16) to 10, (8 to 17) to 11, (8 to 19) to 12,
            (8 to 25) to 13, (8 to 26) to 14, (8 to 27) to 15, (17 to 19) to 18)
        private val FINAL_SPLITS = FINAL_PAIRS.entries.associate { (parts, combined) -> combined to parts }

        fun keyLabel(key: Char): Char? = SHIFTED[key] ?: KEYS.indexOf(key.lowercaseChar()).takeIf { it >= 0 }?.let { JAMO[it] }
    }
}
