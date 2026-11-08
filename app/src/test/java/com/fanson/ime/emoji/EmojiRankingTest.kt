package com.fanson.ime.emoji

import org.junit.Assert.*
import org.junit.Test

class EmojiRankingTest {
    private fun emoji(text: String, variant: Boolean = false) = Emoji(text, "group", text, text, variant)
    @Test fun frequencyOutranksRecency() {
        val result = EmojiRanking.common(listOf(emoji("A"), emoji("B")), mapOf("A" to EmojiUsage(5, 1), "B" to EmojiUsage(2, 99)))
        assertEquals(listOf("A", "B"), result.map { it.text })
    }
    @Test fun equalCountsUseLastUsedOrder() {
        val result = EmojiRanking.common(listOf(emoji("A"), emoji("B")), mapOf("A" to EmojiUsage(2, 1), "B" to EmojiUsage(2, 2)))
        assertEquals(listOf("B", "A"), result.map { it.text })
    }
    @Test fun defaultsFillUnusedSlotsInStableOrder() {
        val all = listOf(emoji("X"), emoji("😂"), emoji("😀"), emoji("Y"))
        assertEquals(listOf("😀", "😂", "X", "Y"), EmojiRanking.common(all, emptyMap()).map { it.text })
    }
    @Test fun commonIsExactlyOnePageAtMost() {
        val result = EmojiRanking.common(List(100) { emoji("E$it") }, emptyMap())
        assertEquals(30, result.size)
    }
    @Test fun unsupportedGlyphsAreRemovedBeforeFillingPage() {
        val result = EmojiRanking.common(List(100) { emoji("E$it") }, emptyMap()) { it.text.drop(1).toInt() >= 40 }
        assertEquals(30, result.size)
        assertEquals("E40", result.first().text)
        assertEquals("E69", result.last().text)
    }
    @Test fun commonlyUsedFlagCanEnterCommonPage() {
        val result = EmojiRanking.common(listOf(emoji("😀"), emoji("🇨🇳")), mapOf("🇨🇳" to EmojiUsage(3, 1)))
        assertEquals("🇨🇳", result.first().text)
    }
    @Test fun usedVariantsCanRankButUnusedVariantsDoNotFillDefaults() {
        val all = listOf(emoji("👍"), emoji("👍🏽", true), emoji("👍🏻", true))
        val result = EmojiRanking.common(all, mapOf("👍🏽" to EmojiUsage(1, 1)))
        assertEquals(listOf("👍🏽", "👍"), result.map { it.text })
    }
    @Test fun rankingDoesNotMutateSourceData() {
        val all = listOf(emoji("B"), emoji("A"))
        val usage = mapOf("A" to EmojiUsage(1, 1))
        EmojiRanking.common(all, usage)
        assertEquals(listOf("B", "A"), all.map { it.text })
        assertEquals(EmojiUsage(1, 1), usage["A"])
    }
}
