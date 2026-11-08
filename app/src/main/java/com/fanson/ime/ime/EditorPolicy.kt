package com.fanson.ime.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.fanson.ime.engine.InputLanguage

object EditorPolicy {
    fun initialLanguage(inputType: Int, preferred: InputLanguage): InputLanguage {
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val latinField = inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT &&
            variation in setOf(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_URI)
        return if (password(inputType) || numeric(inputType) || latinField) InputLanguage.ENGLISH else preferred
    }
    fun password(inputType: Int): Boolean {
        val typeClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return (typeClass == InputType.TYPE_CLASS_TEXT && variation in setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        )) || (typeClass == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
    }
    fun privateField(info: EditorInfo): Boolean = password(info.inputType) || info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0
    fun numeric(inputType: Int): Boolean = (inputType and InputType.TYPE_MASK_CLASS) in setOf(
        InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME)
}
