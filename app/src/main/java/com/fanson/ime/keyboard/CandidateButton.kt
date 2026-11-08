package com.fanson.ime.keyboard

import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.widget.Button
import com.fanson.ime.engine.Candidate

/** Two lines inside the existing header; readings never become committed text. */
fun candidateButton(ui: Ui, candidate: Candidate, readings: Boolean, select: () -> Unit): Button {
    val annotation = if (readings) candidate.reading.ifEmpty { "暂无读音" } else ""
    val displayText = if (readings) candidate.text.replace(Regex("\\s+"), " ") else candidate.text
    val label = if (readings) displayText + "\n" + annotation
        else candidate.text + if (candidate.exact) "" else " · ${candidate.code}"
    return ui.button(label, action = select).apply {
        textSize = 18f
        setPadding(ui.context.dp(12), 0, ui.context.dp(12), 0)
        if (readings) {
            setSingleLine(false); maxLines = 2; ellipsize = null
            includeFontPadding = false
            val start = displayText.length + 1
            text = SpannableString(label).apply {
                setSpan(AbsoluteSizeSpan(12, true), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(ForegroundColorSpan(ui.palette.muted), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            contentDescription = "${candidate.text}，读音：$annotation"
        } else {
            isSingleLine = true; ellipsize = android.text.TextUtils.TruncateAt.END
        }
    }
}
