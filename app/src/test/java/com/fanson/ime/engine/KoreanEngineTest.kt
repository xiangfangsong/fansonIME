package com.fanson.ime.engine

import java.text.Normalizer
import org.junit.Assert.*
import org.junit.Test

class KoreanEngineTest {
    private fun type(engine: KoreanEngine, keys: String) = keys.flatMap { engine.letter(it).commits }.joinToString("")
    private fun text(keys: String): String {
        val e = KoreanEngine()
        return type(e, keys) + e.code
    }

    @Test fun pcKeyboardHasAllLettersAndExactlySevenShiftChanges() {
        assertEquals("ㅂㅈㄷㄱㅅㅛㅕㅑㅐㅔ", "qwertyuiop".map { KoreanEngine.keyLabel(it) }.joinToString(""))
        assertEquals("ㅁㄴㅇㄹㅎㅗㅓㅏㅣ", "asdfghjkl".map { KoreanEngine.keyLabel(it) }.joinToString(""))
        assertEquals("ㅋㅌㅊㅍㅠㅜㅡ", "zxcvbnm".map { KoreanEngine.keyLabel(it) }.joinToString(""))
        assertEquals("ㅃㅉㄸㄲㅆㅒㅖ", "QWERTOP".map { KoreanEngine.keyLabel(it) }.joinToString(""))
        for (key in ('a'..'z').filter { it !in "qwertop" }) assertEquals(KoreanEngine.keyLabel(key), KoreanEngine.keyLabel(key.uppercaseChar()))
        assertNull(KoreanEngine.keyLabel('1'))
    }
    @Test fun everyModernSyllableCanBeTypedAndMatchesUnicodeNormalization() {
        val initials = listOf("r", "R", "s", "e", "E", "f", "a", "q", "Q", "t", "T", "d", "w", "W", "c", "z", "x", "v", "g")
        val vowels = listOf("k", "o", "i", "O", "j", "p", "u", "P", "h", "hk", "ho", "hl", "y", "n", "nj", "np", "nl", "b", "m", "ml", "l")
        val finals = listOf("", "r", "R", "rt", "s", "sw", "sg", "e", "f", "fr", "fa", "fq", "ft", "fx", "fv", "fg", "a", "q", "qt", "t", "T", "d", "w", "c", "z", "x", "v", "g")
        val e = KoreanEngine()
        var count = 0
        for ((l, initial) in initials.withIndex()) for ((v, vowel) in vowels.withIndex()) for ((t, final) in finals.withIndex()) {
            val jamo = "${(0x1100 + l).toChar()}${(0x1161 + v).toChar()}" + if (t > 0) (0x11A7 + t).toChar() else ""
            val expected = Normalizer.normalize(jamo, Normalizer.Form.NFC)
            assertEquals("Premature commit for $initial$vowel$final", "", type(e, initial + vowel + final))
            assertEquals(expected, e.code)
            assertTrue(e.candidates.isEmpty())
            e.reset(); count++
        }
        assertEquals(11172, count)
    }
    @Test fun commonWordsAndCompoundFinalsAreComposed() {
        assertEquals("한글", text("gksrmf"))
        assertEquals("안녕하세요", text("dkssudgktpdy"))
        assertEquals("닭", text("ekfr"))
        assertEquals("값", text("rkqt"))
        assertEquals("왜", text("dho"))
        assertEquals("의", text("dml"))
    }
    @Test fun followingVowelMovesSimpleFinalToNextInitial() {
        assertEquals("가나", text("rksk"))
        assertEquals("가까", text("rkRk"))
        assertEquals("가싸", text("rkTk"))
    }
    @Test fun followingVowelSplitsAllElevenCompoundFinals() {
        val examples = mapOf("rt" to "각사", "sw" to "간자", "sg" to "간하", "fr" to "갈가",
            "fa" to "갈마", "fq" to "갈바", "ft" to "갈사", "fx" to "갈타", "fv" to "갈파", "fg" to "갈하", "qt" to "갑사")
        for ((final, expected) in examples) assertEquals(expected, text("rk" + final + "k"))
    }
    @Test fun repeatedConsonantsUsePcBoundariesAndShiftMakesTenseConsonants() {
        assertEquals("ㄱㄱ", text("rr"))
        assertEquals("각ㄱ", text("rkrr"))
        assertEquals("까", text("Rk"))
        assertEquals("가ㄸ", text("rkE"))
        assertEquals("가ㅏ", text("rkk"))
    }
    @Test fun standaloneVowelsCombineAndStandaloneConsonantsRemainUsable() {
        assertEquals("ㅘ", text("hk"))
        assertEquals("ㅙ", text("ho"))
        assertEquals("ㅢ", text("ml"))
        assertEquals("ㅋㅋㅋ", text("zzz"))
        assertEquals("ㅏㅎ", text("kg"))
    }
    @Test fun deleteUndoesActualKeysInsideTheActiveSyllable() {
        val e = KoreanEngine(); type(e, "rhkrt")
        assertEquals(Normalizer.normalize("\u1100\u116A\u11AA", Normalizer.Form.NFC), e.code)
        for (expected in listOf("곽", "과", "고", "ㄱ", "")) {
            assertTrue(e.backspace()); assertEquals(expected, e.code)
        }
        assertFalse(e.backspace())
    }
    @Test fun deleteShiftVowelDoesNotInventAnUnpressedKey() {
        val e = KoreanEngine(); type(e, "rO")
        assertEquals("걔", e.code)
        e.backspace(); assertEquals("ㄱ", e.code)
    }
    @Test fun completedSyllablesAreNotRetainedInTheActiveHistory() {
        val e = KoreanEngine()
        assertEquals("가", type(e, "rksk"))
        assertEquals("나", e.code)
        e.backspace(); assertEquals("ㄴ", e.code)
        e.backspace(); assertEquals("", e.code)
        assertFalse(e.backspace())
    }
    @Test fun spacePunctuationAndReturnConfirmWithoutRomanLetters() {
        val e = KoreanEngine(); type(e, "gks")
        assertEquals(listOf("한", " "), e.space().commits)
        type(e, "rmf"); assertEquals(listOf("글", "."), e.punctuation(".").commits)
        type(e, "gk"); assertEquals(listOf("하"), e.enter().commits)
        assertEquals("", e.code); assertTrue(e.enter().commits.isEmpty())
    }
    @Test fun resultSeparatesCommittedPrefixFromCurrentComposingText() {
        val e = KoreanEngine(); type(e, "rks")
        val result = e.letter('k')
        assertEquals(listOf("가"), result.commits)
        assertEquals("나", result.composing)
    }
    @Test fun resetInvalidKeyAndCandidateSelectionCannotLeakOldText() {
        val e = KoreanEngine(); type(e, "gks")
        assertTrue(e.letter('1').commits.isEmpty()); assertEquals("한", e.code)
        assertTrue(e.select(0).commits.isEmpty()); assertEquals("한", e.code)
        e.reset(); assertEquals("", e.code); assertTrue(e.resolve().commits.isEmpty())
        assertEquals("ㄱ", e.letter('r').composing)
    }
    @Test fun languageOrderAndStoredValuesAreStable() {
        assertEquals(listOf("English", "五笔86", "한국어"), InputLanguage.entries.map { it.label })
        assertEquals(InputLanguage.WUBI, InputLanguage.fromPreference(""))
        assertEquals(InputLanguage.WUBI, InputLanguage.fromPreference("invalid"))
        for (language in InputLanguage.entries) assertEquals(language, InputLanguage.fromPreference(language.value))
    }
}
