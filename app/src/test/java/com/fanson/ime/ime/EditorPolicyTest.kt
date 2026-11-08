package com.fanson.ime.ime

import android.text.InputType
import org.junit.Assert.*
import org.junit.Test

class EditorPolicyTest {
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
