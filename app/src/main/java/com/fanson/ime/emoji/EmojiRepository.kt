package com.fanson.ime.emoji

import android.content.Context
import org.json.JSONArray

class EmojiRepository(context: Context) {
    val all: List<Emoji>
    init {
        val json = JSONArray(context.assets.open("emoji.json").bufferedReader().use { it.readText() })
        all = List(json.length()) { i -> json.getJSONObject(i).let {
            Emoji(it.getString("text"), it.getString("group"), it.getString("name"), it.getString("keywords"), it.getBoolean("variant"))
        } }
    }
    val categories = linkedMapOf(
        "常用" to "popular", "笑脸" to "Smileys & Emotion",
        "人物" to "People & Body", "动物" to "Animals & Nature", "食物" to "Food & Drink",
        "旅行" to "Travel & Places", "活动" to "Activities", "物品" to "Objects",
        "符号" to "Symbols", "旗帜" to "Flags"
    )
    fun filter(category: String, query: String, usage: Map<String, EmojiUsage>, supported: (Emoji) -> Boolean = { true }): List<Emoji> {
        if (category == "popular" && query.isBlank()) return EmojiRanking.common(all, usage, supported)
        return all.filter { emoji ->
            if (query.isNotBlank()) emoji.keywords.contains(query.trim(), true) || emoji.name.contains(query.trim(), true) || emoji.text == query.trim()
            else emoji.group == category
        }.filter(supported)
    }
}
