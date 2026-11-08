package com.fanson.ime.engine

data class Candidate(val text: String, val code: String, val exact: Boolean = true)

interface Lexicon {
    /** Exact entries first, then explicitly marked prefix completions. */
    fun lookup(code: String): List<Candidate>
}

data class EngineResult(val commits: List<String> = emptyList(), val hint: String? = null, val composing: String? = null)

interface InputEngine {
    val code: String
    val candidates: List<Candidate>
    fun reset()
    fun letter(char: Char): EngineResult
    fun select(index: Int): EngineResult
    fun space(): EngineResult
    fun resolve(): EngineResult
    fun enter(): EngineResult
    fun backspace(): Boolean
    fun punctuation(text: String): EngineResult
}

enum class WubiEnterAction(val value: String, val label: String, val noMatchHint: String) {
    COMMIT_CODE("commit_code", "上屏五笔编码", "无匹配，退格修正 / 回车上屏编码"),
    CLEAR_CODE("clear_code", "清除五笔编码", "无匹配，退格修正 / 回车清除编码"),
    FIRST_CANDIDATE("first_candidate", "上屏首个候选", "无匹配，请退格修正");

    companion object {
        fun fromPreference(value: String): WubiEnterAction = entries.firstOrNull { it.value == value } ?: COMMIT_CODE
    }
}

/** A deterministic four-code table engine. Confined to one input worker. */
class WubiEngine(private val lexicon: Lexicon, private val converter: TraditionalConverter? = null) : InputEngine {
    override var code: String = ""
        private set
    override var candidates: List<Candidate> = emptyList()
        private set
    var autoCommitUnique = true
    var commitOnFifth = true
    var enterAction = WubiEnterAction.COMMIT_CODE
    var traditionalMode = false
        set(value) { if (field != value) { field = value; refresh() } }

    override fun reset() { code = ""; candidates = emptyList() }
    private fun refresh() {
        val source = if (code.isEmpty()) emptyList() else lexicon.lookup(code)
        candidates = if (traditionalMode) converter?.candidates(source) ?: source else source
    }

    override fun letter(char: Char): EngineResult {
        if (char !in 'a'..'y') return EngineResult(hint = "Z 键预留给后续反查功能")
        val commits = mutableListOf<String>()
        if (code.length == 4) {
            if (candidates.isEmpty() || !commitOnFifth) return EngineResult(hint = "请先选词或退格修正")
            commits += candidates.first().text
            reset()
        }
        code += char
        refresh()
        val exact = candidates.filter { it.exact }
        if (code.length == 4 && autoCommitUnique && exact.size == 1) {
            commits += exact.single().text
            reset()
        }
        return EngineResult(commits, if (code.isNotEmpty() && candidates.isEmpty()) "无匹配，请退格修正" else null)
    }

    override fun select(index: Int): EngineResult {
        val selected = candidates.getOrNull(index) ?: return EngineResult()
        reset()
        return EngineResult(listOf(selected.text))
    }

    override fun space(): EngineResult = when {
        code.isEmpty() -> EngineResult(listOf(" "))
        candidates.isEmpty() -> EngineResult(hint = enterAction.noMatchHint)
        else -> select(0)
    }

    /** Resolves preedit before switching a panel; does not invent a space. */
    override fun resolve(): EngineResult = when {
        code.isEmpty() -> EngineResult()
        candidates.isEmpty() -> EngineResult(hint = enterAction.noMatchHint)
        else -> select(0)
    }

    override fun enter(): EngineResult = enter(enterAction)

    fun enter(action: WubiEnterAction): EngineResult {
        if (code.isEmpty()) return EngineResult()
        when (action) {
            WubiEnterAction.CLEAR_CODE -> { reset(); return EngineResult() }
            WubiEnterAction.FIRST_CANDIDATE -> return if (candidates.isEmpty()) EngineResult(hint = action.noMatchHint) else select(0)
            WubiEnterAction.COMMIT_CODE -> Unit
        }
        val raw = code
        reset()
        return EngineResult(listOf(raw))
    }

    override fun backspace(): Boolean {
        if (code.isEmpty()) return false
        code = code.dropLast(1)
        refresh()
        return true
    }

    override fun punctuation(text: String): EngineResult {
        val result = resolve()
        return if (code.isEmpty()) result.copy(commits = result.commits + text) else result
    }
}

object PhraseEncoder {
    /** Takes full single-character codes, never abbreviated codes. */
    fun encode(codes: List<String>): String? {
        if (codes.size < 2 || codes.any { !it.matches(Regex("[a-y]{2,4}")) }) return null
        return when (codes.size) {
            2 -> codes[0].take(2) + codes[1].take(2)
            3 -> codes[0].take(1) + codes[1].take(1) + codes[2].take(2)
            else -> codes.take(3).joinToString("") { it.take(1) } + codes.last().take(1)
        }
    }
}
