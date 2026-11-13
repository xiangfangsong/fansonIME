package com.fanson.ime.data

import android.content.Context
import android.app.Application
import android.os.Process
import com.fanson.ime.engine.WubiFrequency
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** One process-wide model and one background disk queue, shared by settings and IME.
 * No service/activity references are retained. No timer, count trigger or shutdown flush.
 */
class WubiFrequencyStore private constructor(private val app: Application) {
    companion object {
        private val instanceLock = Any()
        @Volatile private var instance: WubiFrequencyStore? = null
        private val io = Executors.newSingleThreadExecutor { task ->
            Thread({ Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND); task.run() }, "wubi-frequency-disk")
        }
        /** Call off the main thread: first use opens and loads the bounded personal table. */
        fun get(context: Context): WubiFrequencyStore = instance ?: synchronized(instanceLock) {
            instance ?: WubiFrequencyStore(context.applicationContext as Application).also { instance = it }
        }
        /** Completion runs on the disk queue. Caller posts UI feedback to the main thread. */
        fun edit(context: Context, operation: (WubiFrequencyStore) -> Unit, completed: (Boolean) -> Unit) {
            val app = context.applicationContext
            io.execute {
                val success = try { operation(get(app)); true } catch (_: Exception) { false }
                completed(success)
            }
        }
    }
    private val store = LocalStore(app)
    val model = WubiFrequency()
    private val saveQueued = AtomicBoolean()
    init { model.load(store.frequencyRecords(), System.currentTimeMillis()) }

    fun saveOnHidden() {
        if (!saveQueued.compareAndSet(false, true)) return
        io.execute {
            saveQueued.set(false)
            // Leave dirty changes available for the next actual hide on failure.
            try { saveChanges() } catch (_: Exception) { /* No input or SQLite values in logs. */ }
        }
    }
    private fun saveChanges() {
        val batch = model.prepareSave(System.currentTimeMillis())
        try { store.saveFrequency(batch) } catch (error: Exception) { model.failed(batch); throw error }
        model.acknowledge(batch)
    }
    /** Explicit deletion is immediately persisted, unlike normal learning. Disk queue only. */
    fun clearFrequency() {
        model.invalidateFeedback()
        store.clearFrequency()
        model.clear()
    }
    fun clearEmojiUsage() { store.clearEmojiUsage() }
    /** Called on the shared disk queue, like explicit clear/delete operations. */
    fun personalBackup(sections: Set<BackupSection>): PersonalBackup = store.personalBackup(
        sections, if (BackupSection.FREQUENCY in sections) model.snapshot(System.currentTimeMillis()) else emptyList()
    )
    fun importPersonalBackup(data: PersonalBackup) {
        PersonalBackupJson.validate(data)
        if (data.wubiFrequency == null) { store.importPersonalBackup(data); return }
        model.beginRestore()
        var committed = false
        try { store.importPersonalBackup(data); committed = true }
        finally { model.finishRestore(data.wubiFrequency.takeIf { committed }, System.currentTimeMillis()) }
    }
    fun removePhrase(code: String, text: String) { removePhrases(listOf(code to text)) }
    fun clearPhrases() { removePhrases(store.phrases().map { it.code to it.text }) }
    private fun removePhrases(phrases: List<Pair<String, String>>) {
        WubiRepository(app, store).use { dictionary ->
            val sources = phrases.associateWith { (code, text) -> dictionary.containsBuiltin(code, text) }
            model.removePhrases(sources)
        }
        // Keep unrelated in-memory learning deferred until the next hide.
        val affected = phrases.toHashSet()
        val deletions = model.prepareSave(System.currentTimeMillis(), affected)
        try { store.deletePhrasesWithFrequency(phrases, deletions) } catch (error: Exception) { model.failed(deletions); throw error }
        model.acknowledge(deletions)
    }
}
