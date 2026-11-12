package com.fanson.ime.engine

import java.text.Normalizer

/** Canonical spellings shared by the offline index and the input engine. */
object PinyinSpelling {
    fun normalize(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD).replace("u\u0308", "v")
        .filter { it in 'a'..'z' || it == '\'' }

    fun key(value: String): String = normalize(value).replace("'", "")

    fun matchesBoundaries(input: String, syllables: String): Boolean {
        if ('\'' !in input) return true
        val boundaries = syllables.split(' ').dropLast(1).runningFold(0) { n, part -> n + part.length }.drop(1).toSet()
        var offset = 0
        for (char in normalize(input)) {
            if (char == '\'') { if (offset !in boundaries) return false } else offset++
        }
        return true
    }
}

/** Internal preedit only. Both permanent and temporary Pinyin run on the IME worker. */
class PinyinEngine(private val lexicon: Lexicon, private val converter: TraditionalConverter? = null) : InputEngine {
    override var code = ""
        private set
    override var candidates: List<Candidate> = emptyList()
        private set
    var traditionalMode = false
        set(value) { if (field != value) { field = value; refresh() } }

    private fun refresh() {
        val source = if (code.isEmpty()) emptyList() else lexicon.lookup(code)
        candidates = if (traditionalMode) converter?.candidates(source) ?: source else source
    }

    override fun reset() { code = ""; candidates = emptyList() }
    override fun letter(char: Char): EngineResult {
        val lower = char.lowercaseChar()
        if (lower !in 'a'..'z' && lower != '\'') return EngineResult()
        if (code.length >= 64) return EngineResult(hint = "拼音过长，请先选词或清空")
        if (lower == '\'' && (code.isEmpty() || code.endsWith('\''))) return EngineResult(hint = "请在音节之间输入分隔符")
        code += lower
        refresh()
        return EngineResult(hint = if (candidates.isEmpty()) "无匹配，退格修正 / 回车上屏拼音" else null)
    }

    override fun select(index: Int): EngineResult {
        val selected = candidates.getOrNull(index) ?: return EngineResult()
        reset()
        return EngineResult(listOf(selected.text))
    }
    override fun space(): EngineResult = if (code.isEmpty()) EngineResult(listOf(" ")) else resolve()
    override fun resolve(): EngineResult = when {
        code.isEmpty() -> EngineResult()
        candidates.isEmpty() -> EngineResult(hint = "无匹配，退格修正 / 回车上屏拼音")
        else -> select(0)
    }
    override fun enter(): EngineResult {
        val raw = code
        reset()
        return if (raw.isEmpty()) EngineResult() else EngineResult(listOf(raw))
    }
    override fun backspace(): Boolean {
        if (code.isEmpty()) return false
        code = code.dropLast(1); refresh()
        return true
    }
    override fun punctuation(text: String): EngineResult {
        if (text == "'" && code.isNotEmpty()) return letter('\'')
        val result = resolve()
        return if (code.isEmpty()) result.copy(commits = result.commits + text) else result
    }
}
