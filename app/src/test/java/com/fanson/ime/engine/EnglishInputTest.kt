package com.fanson.ime.engine

import org.junit.Assert.*
import org.junit.Test

class EnglishInputTest {
    private fun engine() = EnglishEngine(EnglishLexicon(listOf("its", "im", "id", "youre", "apple", "itself", "imagine")))
    private fun type(e: EnglishEngine, word: String) { word.forEach { if (it == '\'') e.punctuation("'") else e.letter(it) } }

    @Test fun automaticConsumesOnlyOneLetterAndManualCapsSurvivesWords() {
        val state = EnglishCapitalization()
        state.refresh(true); assertEquals(EnglishCapitalization.Shift.AUTO, state.shift)
        assertEquals('H', state.letter('h')); assertEquals('i', state.letter('i'))
        state.toggle(); assertEquals(EnglishCapitalization.Shift.CAPS, state.shift)
        state.boundary(); state.refresh(false); assertEquals('A', state.letter('a'))
        state.toggle(); state.refresh(true); assertEquals('a', state.letter('a'))
    }
    @Test fun cancellationSurvivesRefreshAndDeletionUntilBoundaryOrNavigation() {
        val state = EnglishCapitalization()
        state.refresh(true); state.toggle(); state.refresh(true)
        assertEquals(EnglishCapitalization.Shift.LOWER, state.shift)
        assertEquals('a', state.letter('a')); state.refresh(true) // deleted back to the same empty word
        assertEquals('b', state.letter('b'))
        state.boundary(); state.refresh(true); assertEquals('C', state.letter('c'))
        state.refresh(true); state.toggle(); state.positionChanged(); state.refresh(true)
        assertEquals(EnglishCapitalization.Shift.AUTO, state.shift)
    }
    @Test fun cancelledAutomaticCanEnterPersistentCapsAndSessionResetsIt() {
        val state = EnglishCapitalization()
        state.refresh(true); state.toggle(); state.toggle()
        assertEquals(EnglishCapitalization.Shift.CAPS, state.shift)
        state.reset(); assertEquals(EnglishCapitalization.Shift.LOWER, state.shift)
        state.reset(locked = true); assertEquals('A', state.letter('a'))
    }
    @Test fun exactShortcutsArePinnedAndOriginalIsSecondWithoutChangingSpace() {
        for ((input, output) in mapOf("im" to "I'm", "id" to "I'd", "its" to "it's", "youre" to "you're", "dont" to "don't")) {
            val e = engine(); type(e, input)
            assertEquals(listOf(output, input), e.candidates.take(2).map { it.text })
            assertEquals(listOf(input, " "), e.space().commits)
            type(e, input); assertEquals(listOf(output), e.select(0).commits)
        }
    }
    @Test fun shortPrefixesAndExcludedAmbiguousWordsDoNotGetContractions() {
        val lexicon = EnglishLexicon(listOf("i", "it", "you", "were", "well", "ill", "imagine"))
        for (prefix in listOf("i", "it", "you", "were", "well", "ill", "imagine")) {
            assertFalse(lexicon.lookup(prefix).any { '\'' in it.text })
        }
    }
    @Test fun contractionCaseMatchesTheTypedWordAndCapitalIIsConventional() {
        val lexicon = EnglishLexicon(emptyList())
        for ((input, output) in mapOf("Im" to "I'm", "IM" to "I'M", "Its" to "It's", "ITS" to "IT'S", "Youre" to "You're", "YOURE" to "YOU'RE")) {
            assertEquals(output, lexicon.lookup(input).first().text)
            assertEquals(input, lexicon.lookup(input)[1].text)
        }
    }
    @Test fun explicitApostropheStaysInCompositionAndDeletionRestoresShortcut() {
        val e = engine(); type(e, "you")
        val r = e.punctuation("'")
        assertTrue(r.commits.isEmpty()); assertEquals("you'", r.composing)
        type(e, "re"); assertEquals("you're", e.code); assertEquals("you're", e.candidates.first().text)
        repeat(3) { assertTrue(e.backspace()) }; assertEquals("you", e.code)
        type(e, "re"); assertEquals("you're", e.candidates.first().text)
    }
    @Test fun delimitersRemainLiteralAndDisabledModeDoesNotComposeOrSuggest() {
        val e = engine(); type(e, "its"); assertEquals(listOf("its", "."), e.punctuation(".").commits)
        type(e, "im"); assertEquals(listOf("im"), e.enter().commits)
        assertEquals(listOf("'"), e.punctuation("'").commits)
        e.enabled = false
        assertEquals(listOf("i"), e.letter('i').commits)
        assertEquals(listOf("'"), e.punctuation("'").commits)
        assertTrue(e.candidates.isEmpty() && e.code.isEmpty())
    }
    @Test fun priorityKeepsTheCandidateLimitAndDeduplicatesDictionaryMatches() {
        val lexicon = EnglishLexicon(listOf("its", "it's") + (0..100).map { "its" + ('a' + it / 26) + ('a' + it % 26) })
        val result = lexicon.lookup("its")
        assertEquals(80, result.size); assertEquals(80, result.map { it.text }.distinct().size)
        assertEquals(listOf("it's", "its"), result.take(2).map { it.text })
    }
    @Test fun apostropheCannotGrowAnUnboundedPreeditOrDuplicateInsideAWord() {
        val e = engine(); type(e, "a".repeat(64)); assertEquals(listOf("a".repeat(64), "'"), e.punctuation("'").commits)
        type(e, "you're"); assertEquals(listOf("you're", "'"), e.punctuation("'").commits)
    }
}
