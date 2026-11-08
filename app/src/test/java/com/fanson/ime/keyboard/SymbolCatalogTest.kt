package com.fanson.ime.keyboard

import org.junit.Assert.*
import org.junit.Test

class SymbolCatalogTest {
    private fun symbols(name: String) = SymbolCatalog.categories.first { it.name == name }.symbols
    @Test fun categoriesHaveExplicitFixedOrderAndCommonFirst() {
        assertEquals(listOf("常用", "中文", "英文", "箭头", "数学", "序号", "单位", "货币", "括号", "图形", "希腊", "制表"), SymbolCatalog.categories.map { it.name })
    }
    @Test fun englishIncludesAll32AsciiPunctuationCharacters() {
        val expected = (33..126).map { it.toChar() }.filterNot { it.isLetterOrDigit() }.map { it.toString() }.toSet()
        assertEquals(32, symbols("英文").size)
        assertEquals(expected, symbols("英文").toSet())
        assertTrue(symbols("英文").containsAll(listOf("\$", "`", "\\", "\"", "'", "^", ":")))
    }
    @Test fun categoriesContainRelevantSpecialCharacters() {
        mapOf("箭头" to "→", "数学" to "∞", "序号" to "⑳", "单位" to "℃", "货币" to "€", "括号" to "【", "图形" to "✓", "希腊" to "α", "制表" to "┼").forEach { (name, symbol) -> assertTrue("$name / $symbol", symbol in symbols(name)) }
    }
    @Test fun multiCharacterEntriesRemainWhole() {
        assertTrue("……" in symbols("中文"))
        assertTrue("——" in symbols("中文"))
        assertTrue(symbols("单位").containsAll(listOf("m²", "m³", "km/h", "kcal")))
    }
    @Test fun everyCategoryIsNonemptyAndHasNoDuplicates() {
        SymbolCatalog.categories.forEach {
            assertTrue(it.symbols.isNotEmpty())
            assertTrue(it.symbols.none { symbol -> symbol.isBlank() })
            assertEquals(it.symbols.size, it.symbols.toSet().size)
        }
        assertTrue(SymbolCatalog.categories.sumOf { it.symbols.size } > 600)
    }
}
