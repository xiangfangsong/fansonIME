package com.fanson.ime.emoji

data class Emoji(val text: String, val group: String, val name: String, val keywords: String, val variant: Boolean)
data class EmojiUsage(val count: Long, val lastUsed: Long)

object EmojiRanking {
    const val COMMON_LIMIT = 30
    private val defaults = listOf("😀", "😄", "😂", "🤣", "😊", "🥰", "😍", "😎", "😭", "😅", "🤔", "👍", "👏", "🙏", "❤️", "🎉", "🔥", "💪", "🌹", "☀️", "✅", "👌", "🤝", "💯")
    private val defaultRank = defaults.withIndex().associate { it.value to it.index }

    fun common(all: List<Emoji>, usage: Map<String, EmojiUsage>, supported: (Emoji) -> Boolean = { true }): List<Emoji> =
        all.withIndex().filter { !it.value.variant || (usage[it.value.text]?.count ?: 0L) > 0L }
            .sortedWith(compareByDescending<IndexedValue<Emoji>> { usage[it.value.text]?.count ?: 0L }
                .thenByDescending { usage[it.value.text]?.lastUsed ?: 0L }
                .thenBy { defaultRank[it.value.text] ?: Int.MAX_VALUE }
                .thenBy { it.index })
            .asSequence().map { it.value }.filter(supported).take(COMMON_LIMIT).toList()
}
