package com.fanson.ime.engine

/** OpenCC dictionary data, with phrase-first longest matching. No native library. */
class TraditionalConverter(private val mappings: Map<String, List<String>>) {
    private val maxLength = mappings.keys.maxOfOrNull { it.length } ?: 1

    fun convert(text: String): List<String> {
        mappings[text]?.let { return it }
        val result = StringBuilder()
        var offset = 0
        while (offset < text.length) {
            var matched = false
            for (length in minOf(maxLength, text.length - offset) downTo 1) {
                val alternatives = mappings[text.substring(offset, offset + length)] ?: continue
                result.append(alternatives.first())
                offset += length
                matched = true
                break
            }
            if (!matched) {
                val count = Character.charCount(text.codePointAt(offset))
                result.append(text, offset, offset + count)
                offset += count
            }
        }
        return listOf(result.toString())
    }

    fun candidates(source: List<Candidate>): List<Candidate> = source.flatMap { candidate ->
        convert(candidate.text).map { candidate.copy(text = it) }
    }.distinctBy { Triple(it.text, it.code, it.exact) }

    companion object {
        fun read(lines: Sequence<String>) = TraditionalConverter(lines.filter { it.isNotBlank() }.associate {
            val parts = it.split('\t', limit = 2)
            parts[0] to parts[1].split(' ').distinct()
        })
    }
}
