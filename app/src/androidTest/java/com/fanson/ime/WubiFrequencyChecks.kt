package com.fanson.ime

import android.app.Instrumentation
import android.database.sqlite.SQLiteDatabase
import com.fanson.ime.data.LocalStore
import com.fanson.ime.data.WubiFrequencyStore
import com.fanson.ime.data.WubiRepository
import com.fanson.ime.engine.*
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Synthetic fixtures only, invoked by the emulator-only smoke runner. */
object WubiFrequencyChecks {
    fun edit(test: Instrumentation, action: (WubiFrequencyStore) -> Unit) {
        val done = CountDownLatch(1)
        var success = false
        WubiFrequencyStore.edit(test.targetContext, action) { success = it; done.countDown() }
        check(done.await(15, TimeUnit.SECONDS) && success) { "Frequency disk operation failed or timed out" }
    }
    fun run(test: Instrumentation): List<String> {
        val passed = mutableListOf<String>()
        val context = test.targetContext
        val fixture = File(context.cacheDir, "frequency-v1-fixture.db")
        SQLiteDatabase.deleteDatabase(fixture)
        try {
            SQLiteDatabase.openOrCreateDatabase(fixture, null).use { old ->
                old.execSQL("CREATE TABLE preferences(key TEXT PRIMARY KEY,value TEXT NOT NULL)")
                old.execSQL("CREATE TABLE phrases(code TEXT NOT NULL,text TEXT NOT NULL,PRIMARY KEY(code,text))")
                old.execSQL("CREATE TABLE emoji_usage(text TEXT PRIMARY KEY,uses INTEGER NOT NULL,last_used INTEGER NOT NULL)")
                old.execSQL("CREATE TABLE clipboard_history(id INTEGER PRIMARY KEY AUTOINCREMENT,text TEXT NOT NULL UNIQUE)")
                old.execSQL("INSERT INTO preferences VALUES('theme','dark')")
                old.execSQL("INSERT INTO phrases VALUES('abc','合成迁移短语')")
                old.execSQL("INSERT INTO emoji_usage VALUES('😀',7,8)")
                old.execSQL("INSERT INTO clipboard_history(text) VALUES('合成迁移剪贴板')")
                old.version = 1
            }
            LocalStore(fixture).use { migrated ->
                check(migrated.get("theme", "") == "dark")
                check(migrated.get("wubi_frequency", "true") == "true")
                check(migrated.phrases().single().text == "合成迁移短语")
                check(migrated.emojiUsage()["😀"]?.count == 7L)
                check(migrated.clipboardHistory().single().text == "合成迁移剪贴板")
                val model = WubiFrequency()
                model.record("a", Candidate("合成学习", "abcd", false), model.epoch, System.currentTimeMillis())
                migrated.saveFrequency(model.prepareSave(System.currentTimeMillis()))
                check(migrated.frequencyRecords().single().key.inputCode == "a")
            }
            SQLiteDatabase.openDatabase(fixture.path, null, SQLiteDatabase.OPEN_READONLY).use {
                check(it.version == 2)
                it.rawQuery("PRAGMA integrity_check", null).use { result -> check(result.moveToFirst() && result.getString(0) == "ok") }
            }
            passed += "frequency_version1_fixture_migration_retains_all_personal_data"
            LocalStore(fixture).use { check(it.frequencyRecords().size == 1) }
            passed += "frequency_sqlite_batch_and_reopen"
        } finally { SQLiteDatabase.deleteDatabase(fixture) }

        val shared = WubiFrequencyStore.get(context)
        edit(test) { it.clearFrequency(); it.clearPhrases(); it.clearEmojiUsage() }
        val model = shared.model
        val now = System.currentTimeMillis()
        val builtin: Candidate
        LocalStore(context).use { store ->
            WubiRepository(context, store).use { dictionary ->
                builtin = dictionary.lookup("a").first()
                val synthetic = Candidate("历史不应制造候选", "abcd", false)
                check(dictionary.lookupRemembered("a", listOf(synthetic)).none { it.text == synthetic.text })
                passed += "frequency_recall_revalidates_dictionary_source"
                // Find a real prefix item cut off by the old 40-item result, then recall it.
                val baseline = dictionary.lookup("a")
                val exact = dictionary.lookup("aaaa")
                val outside = exact.firstOrNull { row -> baseline.none { it.text == row.text } }
                check(outside != null)
                val recalled = dictionary.lookupRemembered("a", listOf(outside))
                check(recalled.any { it.text == outside.text && it.code == "aaaa" && !it.exact })
                passed += "frequency_recalls_real_candidate_beyond_old_prefix_cutoff"
            }
            check(model.record("a", builtin, model.epoch, now))
            check(store.frequencyRecords().isEmpty())
            passed += "frequency_learning_stays_in_memory_before_hide"
            shared.saveOnHidden(); edit(test) {}
            check(store.frequencyRecords().size == 1)
            val previous = store.frequencyRecords()
            shared.saveOnHidden(); edit(test) {}
            check(store.frequencyRecords() == previous && model.pendingSize == 0)
            passed += "frequency_hide_persists_changes_and_unchanged_hide_is_noop"
            store.addPhrase("a", builtin.text)
            store.addPhrase("abcd", "仅自定义的合成词")
            model.record("a", builtin.copy(custom = true), model.epoch, now)
            model.record("abcd", Candidate("仅自定义的合成词", "abcd", custom = true), model.epoch, now)
            store.recordEmojiUsage("😀")
            store.recordClipboard("合成清空隔离验证")
            edit(test) { it.clearPhrases() }
            check(store.phrases().isEmpty())
            check(model.size == 1 && model.recall("a", now).single().text == builtin.text)
            check(store.emojiUsage()["😀"]?.count == 1L)
            check(store.clipboardHistory().first().text == "合成清空隔离验证")
            passed += "frequency_clear_phrases_removes_only_invalid_learning_preserves_builtin_duplicate"
            edit(test) { it.clearEmojiUsage() }
            check(store.emojiUsage().isEmpty() && model.size == 1)
            passed += "frequency_clear_emoji_keeps_wubi_learning"
            val oldEpoch = model.epoch
            shared.saveOnHidden()
            edit(test) { it.clearFrequency() }
            check(!model.record("a", builtin, oldEpoch, now))
            shared.saveOnHidden(); edit(test) {}
            check(store.frequencyRecords().isEmpty() && model.size == 0)
            check(store.clipboardHistory().first().text == "合成清空隔离验证")
            store.clearClipboardHistory()
            passed += "frequency_clear_rejects_old_feedback_and_no_queued_save_resurrection"
        }
        performance(test)
        passed += "frequency_synthetic_model_repository_memory_and_disk_measurements"
        edit(test) { it.clearFrequency() }
        return passed
    }

    private fun performance(test: Instrumentation) {
        val now = System.currentTimeMillis()
        val report = JSONObject().put("environment", "API31 emulator; debug; synthetic data; not phone results")
        fun measure(block: () -> Unit): JSONObject {
            repeat(50) { block() }
            val samples = LongArray(300) {
                val start = System.nanoTime(); block(); System.nanoTime() - start
            }.sorted()
            return JSONObject().put("p50_ms", samples[149] / 1e6).put("p95_ms", samples[284] / 1e6).put("p99_ms", samples[296] / 1e6)
        }
        val runtime = Runtime.getRuntime()
        fun collect() { repeat(3) { System.gc(); System.runFinalization(); android.os.SystemClock.sleep(100) } }
        collect()
        val before = runtime.totalMemory() - runtime.freeMemory()
        val model = WubiFrequency()
        repeat(20_000) { index ->
            model.record("a", Candidate("合成词$index", "aaaa", false), model.epoch, now)
        }
        collect()
        report.put("model_20000_approx_heap_delta_bytes", runtime.totalMemory() - runtime.freeMemory() - before)
        val candidates = List(200) { Candidate("合成词$it", "aaaa", false) }
        val empty = WubiFrequency()
        report.put("rank_200_candidates_no_learning", measure { empty.rank("a", candidates, now) })
        report.put("rank_200_candidates_20000_same_prefix", measure { model.rank("a", candidates, now) })
        report.put("recall64_from_20000_same_prefix", measure { model.recall("a", now) })
        report.put("record_at_capacity", measure { model.record("a", candidates[0], model.epoch, now) })
        val distributed = WubiFrequency()
        repeat(20_000) { index ->
            var value = index
            val code = buildString { repeat(4) { append('a' + value % 25); value /= 25 } }
            distributed.record(code.take(2), Candidate("分散合成词$index", code, false), distributed.epoch, now)
        }
        report.put("recall_from_20000_records_across625_input_codes", measure { distributed.recall("aa", now) })
        LocalStore(test.targetContext).use { store ->
            WubiRepository(test.targetContext, store).use { dictionary ->
                report.put("dictionary_fixed_a", measure { dictionary.lookup("a") })
                val engine = WubiEngine(dictionary, frequency = model, clock = { now })
                report.put("engine_learning_a_with64_invalid_recall_sources", measure { engine.reset(); engine.letter('a') })
            }
        }
        val file = File(test.targetContext.cacheDir, "frequency-performance-fixture.db")
        SQLiteDatabase.deleteDatabase(file)
        try {
            LocalStore(file).use { store ->
                val prepareStart = System.nanoTime()
                val batch = model.prepareSave(now)
                report.put("hidden_trim_and_snapshot_ms", (System.nanoTime() - prepareStart) / 1e6)
                val start = System.nanoTime()
                store.saveFrequency(batch)
                report.put("hidden_initial_18000_rows_transaction_ms", (System.nanoTime() - start) / 1e6)
                model.acknowledge(batch)
                model.record("a", candidates[0], model.epoch, now)
                val update = model.prepareSave(now)
                val nextStart = System.nanoTime()
                store.saveFrequency(update)
                report.put("hidden_one_row_transaction_ms", (System.nanoTime() - nextStart) / 1e6)
                model.acknowledge(update)
                val expireStart = System.nanoTime()
                model.prepareSave(now + WubiFrequency.HALF_LIFE_MS * 6)
                report.put("hidden_expire18000_and_snapshot_ms", (System.nanoTime() - expireStart) / 1e6)
            }
        } finally { SQLiteDatabase.deleteDatabase(file) }
        val directory = File(test.targetContext.externalCacheDir, "qa").apply { mkdirs() }
        File(directory, "frequency-performance-0.4.0.json").writeText(report.toString(2))
    }
}
