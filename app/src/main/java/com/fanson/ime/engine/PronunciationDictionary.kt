package com.fanson.ime.engine

fun interface PronunciationLexicon {
    fun reading(text: String): String
}

/** Exact phrase readings take priority. Fallback never invents combinations of polyphones. */
class PronunciationDictionary(private val find: (String) -> List<String>) : PronunciationLexicon {
    override fun reading(text: String): String {
        val exact = find(text).filter { it.isNotBlank() }.distinct()
        if (exact.isNotEmpty()) return exact.joinToString(" / ")
        val points = text.codePoints().toArray()
        if (points.size <= 1) return "暂无读音"
        return "逐字：" + points.joinToString(" ") { point ->
            val char = String(Character.toChars(point))
            val values = find(char).filter { it.isNotBlank() }.distinct()
            if (values.isEmpty()) "暂无读音" else values.joinToString("/")
        }
    }
}
