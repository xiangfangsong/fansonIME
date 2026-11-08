package com.fanson.ime.clipboard

import android.content.ClipboardManager
import android.content.Context
import android.provider.Settings

data class ClipboardSnapshot(val kind: Kind, val text: String? = null, val sensitive: Boolean = false) {
    enum class Kind { TEXT, EMPTY, NON_TEXT, UNAVAILABLE }
}

/** The primary clip is never cached here, persisted, logged, or coerced from a URI. */
class ClipboardController(context: Context) {
    private val manager = context.getSystemService(ClipboardManager::class.java)
    private val appContext = context.applicationContext
    private var listener: ClipboardManager.OnPrimaryClipChangedListener? = null

    private fun isDefaultIme(): Boolean = Settings.Secure.getString(appContext.contentResolver,
        Settings.Secure.DEFAULT_INPUT_METHOD)?.startsWith("${appContext.packageName}/") == true

    fun read(): ClipboardSnapshot {
        return try {
        if (!isDefaultIme()) return ClipboardSnapshot(ClipboardSnapshot.Kind.UNAVAILABLE)
        val clip = manager.primaryClip
        when {
            clip == null -> ClipboardSnapshot(if (manager.hasPrimaryClip()) ClipboardSnapshot.Kind.UNAVAILABLE else ClipboardSnapshot.Kind.EMPTY)
            clip.itemCount == 0 -> ClipboardSnapshot(ClipboardSnapshot.Kind.EMPTY)
            else -> {
                val texts = (0 until clip.itemCount).map { clip.getItemAt(it).text }
                val text = if (texts.all { it != null }) texts.joinToString("\n") else null
                if (text == null) ClipboardSnapshot(ClipboardSnapshot.Kind.NON_TEXT)
                else ClipboardSnapshot(ClipboardSnapshot.Kind.TEXT, text.toString(),
                    clip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true)
            }
        }
        } catch (_: SecurityException) { ClipboardSnapshot(ClipboardSnapshot.Kind.UNAVAILABLE) }
    }

    fun clear(): Boolean = try { if (!isDefaultIme()) false else { manager.clearPrimaryClip(); !manager.hasPrimaryClip() } }
    catch (_: SecurityException) { false }

    fun observe(changed: () -> Unit) {
        stop()
        listener = ClipboardManager.OnPrimaryClipChangedListener(changed).also { manager.addPrimaryClipChangedListener(it) }
    }
    fun stop() { listener?.let { manager.removePrimaryClipChangedListener(it) }; listener = null }
}
