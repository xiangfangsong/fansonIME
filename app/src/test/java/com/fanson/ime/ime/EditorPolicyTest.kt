package com.fanson.ime.ime

import android.text.InputType
import org.junit.Assert.*
import org.junit.Test
import com.fanson.ime.engine.InputLanguage

class EditorPolicyTest {
    @Test fun sentenceRequestAlsoAcceptsThePlatformParagraphStartWordFlag() {
        assertTrue(EditorPolicy.sentenceStart(InputType.TYPE_TEXT_FLAG_CAP_WORDS))
        assertTrue(EditorPolicy.sentenceStart(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES))
        assertFalse(EditorPolicy.sentenceStart(0))
        assertFalse(EditorPolicy.sentenceStart(InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS))
    }
    @Test fun englishAutomaticCaseOnlyAppliesToProseText() {
        assertTrue(EditorPolicy.englishAutoCapitalize(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE))
        for (variation in listOf(InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_URI, InputType.TYPE_TEXT_VARIATION_FILTER)) {
            assertFalse(EditorPolicy.englishAutoCapitalize(InputType.TYPE_CLASS_TEXT or variation))
        }
        assertFalse(EditorPolicy.englishAutoCapitalize(InputType.TYPE_CLASS_NUMBER))
        assertFalse(EditorPolicy.englishAutoCapitalize(InputType.TYPE_NULL))
    }
    @Test fun ordinaryTextRespectsTheLastChosenLanguage() {
        for (language in InputLanguage.entries) assertEquals(language, EditorPolicy.initialLanguage(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE, language))
    }
    @Test fun latinAndNumericFieldsOverrideWithoutChangingThePreferredLanguage() {
        for (type in listOf(InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)) {
            assertEquals(InputLanguage.ENGLISH, EditorPolicy.initialLanguage(type, InputLanguage.KOREAN))
        }
    }
    @Test fun detectsAllPasswordVariationsWithoutTreatingPlainTextAsPassword() {
        listOf(InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD).forEach {
            assertTrue(EditorPolicy.password(InputType.TYPE_CLASS_TEXT or it))
        }
        assertTrue(EditorPolicy.password(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD))
        assertFalse(EditorPolicy.password(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE))
        assertFalse(EditorPolicy.password(InputType.TYPE_CLASS_NUMBER))
    }
    @Test fun usesNumberLayoutForNumericPhoneAndDateFields() {
        assertTrue(EditorPolicy.numeric(InputType.TYPE_CLASS_NUMBER))
        assertTrue(EditorPolicy.numeric(InputType.TYPE_CLASS_PHONE))
        assertTrue(EditorPolicy.numeric(InputType.TYPE_CLASS_DATETIME))
        assertFalse(EditorPolicy.numeric(InputType.TYPE_CLASS_TEXT))
    }
}
