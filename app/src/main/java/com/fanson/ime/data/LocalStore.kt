package com.fanson.ime.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.fanson.ime.engine.Candidate
import com.fanson.ime.engine.Lexicon
import com.fanson.ime.engine.FrequencyKey
import com.fanson.ime.engine.FrequencyRecord
import com.fanson.ime.engine.FrequencyBatch
import com.fanson.ime.emoji.EmojiUsage
import java.io.File

data class ClipboardEntry(val id: Long, val text: String)

/** Private no-backup storage, including the user's requested ten-item history. */
class LocalStore(file: File) : AutoCloseable {
    constructor(context: Context) : this(File(context.noBackupFilesDir, "personal.db"))
    companion object { private val schemaLock = Any() }
    private val db = SQLiteDatabase.openOrCreateDatabase(file, null)
    init {
        synchronized(schemaLock) {
            // Readers should not wait for a hidden-keyboard frequency write transaction.
            db.enableWriteAheadLogging()
            db.rawQuery("PRAGMA secure_delete=ON", null).use { it.moveToFirst() }
            db.beginTransaction()
            try {
                db.execSQL("CREATE TABLE IF NOT EXISTS preferences(key TEXT PRIMARY KEY,value TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS phrases(code TEXT NOT NULL,text TEXT NOT NULL,PRIMARY KEY(code,text))")
                db.execSQL("CREATE TABLE IF NOT EXISTS clipboard_history(id INTEGER PRIMARY KEY AUTOINCREMENT,text TEXT NOT NULL UNIQUE)")
                db.execSQL("CREATE TABLE IF NOT EXISTS emoji_usage(text TEXT PRIMARY KEY,uses INTEGER NOT NULL,last_used INTEGER NOT NULL)")
                if (db.version < 1) db.execSQL("DROP TABLE IF EXISTS favorites")
                if (db.version < 2) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS wubi_frequency(input_code TEXT NOT NULL,source_code TEXT NOT NULL,source_text TEXT NOT NULL,text TEXT NOT NULL,score REAL NOT NULL,updated_at INTEGER NOT NULL,custom INTEGER NOT NULL,PRIMARY KEY(input_code,source_code,source_text,text))")
                    db.version = 2
                }
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        }
    }
    @Synchronized fun get(key: String, default: String): String = db.rawQuery(
        "SELECT value FROM preferences WHERE key=?", arrayOf(key)
    ).use { if (it.moveToFirst()) it.getString(0) else default }
    @Synchronized fun put(key: String, value: String) {
        db.execSQL("INSERT OR REPLACE INTO preferences VALUES(?,?)", arrayOf(key, value))
    }
    @Synchronized fun putAll(values: Map<String, String>) {
        db.beginTransaction()
        try {
            values.forEach { (key, value) -> put(key, value) }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    @Synchronized fun phrases(code: String? = null): List<Candidate> {
        val sql = if (code == null) "SELECT text,code FROM phrases ORDER BY code,text" else "SELECT text,code FROM phrases WHERE code=? ORDER BY text"
        return db.rawQuery(sql, code?.let { arrayOf(it) }).use { cursor ->
            buildList { while (cursor.moveToNext()) add(Candidate(cursor.getString(0), cursor.getString(1), custom = true)) }
        }
    }
    @Synchronized fun addPhrase(code: String, text: String) {
        require(code.matches(Regex("[a-y]{1,4}")) && text.isNotBlank() && text.length <= 200)
        db.execSQL("INSERT OR REPLACE INTO phrases VALUES(?,?)", arrayOf(code, text))
    }
    @Synchronized fun removePhrase(code: String, text: String) {
        db.execSQL("DELETE FROM phrases WHERE code=? AND text=?", arrayOf(code, text))
    }
    @Synchronized fun emojiUsage(): Map<String, EmojiUsage> = db.rawQuery("SELECT text,uses,last_used FROM emoji_usage", null).use {
        buildMap { while (it.moveToNext()) put(it.getString(0), EmojiUsage(it.getLong(1), it.getLong(2))) }
    }
    @Synchronized fun recordEmojiUsage(text: String) {
        db.beginTransaction()
        try {
            val next = db.rawQuery("SELECT COALESCE(MAX(last_used),0)+1 FROM emoji_usage", null).use { it.moveToFirst(); it.getLong(0) }
            db.execSQL("INSERT INTO emoji_usage(text,uses,last_used) VALUES(?,1,?) ON CONFLICT(text) DO UPDATE SET uses=uses+1,last_used=excluded.last_used", arrayOf(text, next))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    @Synchronized fun clearEmojiUsage() { db.execSQL("DELETE FROM emoji_usage") }
    @Synchronized fun clearPhrases() { db.execSQL("DELETE FROM phrases") }
    @Synchronized fun frequencyRecords(): List<FrequencyRecord> = db.rawQuery(
        "SELECT input_code,source_code,source_text,text,score,updated_at,custom FROM wubi_frequency ORDER BY updated_at DESC LIMIT 20000", null
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(FrequencyRecord(
                FrequencyKey(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3)),
                cursor.getDouble(4), cursor.getLong(5), cursor.getInt(6) != 0
            ))
        }
    }
    @Synchronized fun saveFrequency(batch: FrequencyBatch) {
        if (batch.changes.isEmpty()) return
        db.beginTransaction()
        try {
            batch.changes.forEach { (key, change) ->
                val row = change.record
                if (row == null) db.execSQL("DELETE FROM wubi_frequency WHERE input_code=? AND source_code=? AND source_text=? AND text=?",
                    arrayOf(key.inputCode, key.sourceCode, key.sourceText, key.text))
                else db.execSQL("INSERT OR REPLACE INTO wubi_frequency VALUES(?,?,?,?,?,?,?)",
                    arrayOf(key.inputCode, key.sourceCode, key.sourceText, key.text, row.score, row.updatedAt, if (row.custom) 1 else 0))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    @Synchronized fun clearFrequency() { db.execSQL("DELETE FROM wubi_frequency") }
    @Synchronized fun deletePhrasesWithFrequency(phrases: List<Pair<String, String>>, batch: FrequencyBatch) {
        db.beginTransaction()
        try {
            phrases.forEach { (code, text) -> removePhrase(code, text) }
            saveFrequency(batch)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    @Synchronized fun clipboardHistory(): List<ClipboardEntry> = db.rawQuery(
        "SELECT id,text FROM clipboard_history ORDER BY id DESC LIMIT 10", null
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(ClipboardEntry(cursor.getLong(0), cursor.getString(1))) } }

    @Synchronized fun recordClipboard(text: String, sensitive: Boolean = false) {
        if (sensitive || text.isBlank()) return
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM clipboard_history WHERE text=?", arrayOf(text))
            db.execSQL("INSERT INTO clipboard_history(text) VALUES(?)", arrayOf(text))
            db.execSQL("DELETE FROM clipboard_history WHERE id NOT IN (SELECT id FROM clipboard_history ORDER BY id DESC LIMIT 10)")
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    @Synchronized fun deleteClipboardEntry(id: Long) {
        db.execSQL("DELETE FROM clipboard_history WHERE id=?", arrayOf(id))
    }
    @Synchronized fun clearClipboardHistory() { db.execSQL("DELETE FROM clipboard_history") }
    @Synchronized override fun close() = db.close()
}

/** Open and query only from the IME worker. SQL values are always bound. */
class WubiRepository(context: Context, private val personal: LocalStore) : Lexicon, AutoCloseable {
    private val db: SQLiteDatabase
    init {
        val info = org.json.JSONObject(context.assets.open("data_info.json").bufferedReader().use { it.readText() })
        val checksum = info.getJSONObject("assets").getString("wubi86.db")
        val file = File(context.noBackupFilesDir, "wubi-${checksum.take(12)}.db")
        if (!file.exists()) {
            val temp = File(context.noBackupFilesDir, "wubi.tmp")
            context.assets.open("wubi86.db").use { input -> temp.outputStream().use { input.copyTo(it) } }
            val actual = java.security.MessageDigest.getInstance("SHA-256").digest(temp.readBytes()).joinToString("") { "%02x".format(it) }
            check(actual == checksum) { "码表校验失败" }
            check(temp.renameTo(file)) { "无法准备码表" }
        }
        db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
    }
    override fun lookup(code: String): List<Candidate> {
        require(code.matches(Regex("[a-y]{1,4}")))
        val result = personal.phrases(code).toMutableList()
        val seen = result.map { it.text }.toMutableSet()
        db.rawQuery("SELECT text,code FROM entries WHERE code=? ORDER BY weight DESC,text LIMIT 200", arrayOf(code)).use {
            while (it.moveToNext()) if (seen.add(it.getString(0))) result += Candidate(it.getString(0), it.getString(1))
        }
        if (code.length < 4 && result.size < 40) {
            db.rawQuery("SELECT text,code FROM entries WHERE code>? AND code<? ORDER BY length(code),weight DESC,text LIMIT 80", arrayOf(code, code + "z")).use {
                while (it.moveToNext() && result.size < 40) if (seen.add(it.getString(0))) result += Candidate(it.getString(0), it.getString(1), false)
            }
        }
        return result
    }
    override fun lookupRemembered(code: String, remembered: List<Candidate>): List<Candidate> {
        val result = lookup(code).toMutableList()
        val seen = result.map { it.text }.toMutableSet()
        val missing = remembered.filter {
            it.text !in seen && it.code.startsWith(code) && it.code.length in code.length..4 && it.code.all { char -> char in 'a'..'y' }
        }.distinctBy { it.code to it.text }.take(64)
        // Bound both SQL length and bind count. Revalidate source rows; history is not a dictionary.
        missing.chunked(64).forEach { chunk ->
            val where = chunk.joinToString(" OR ") { "(code=? AND text=?)" }
            val args = chunk.flatMap { listOf(it.code, it.text) }.toTypedArray()
            db.rawQuery("SELECT text,code FROM entries WHERE $where ORDER BY length(code),weight DESC,text", args).use {
                while (it.moveToNext()) if (seen.add(it.getString(0))) result += Candidate(it.getString(0), it.getString(1), it.getString(1) == code)
            }
        }
        return result.filter { it.exact } + result.filterNot { it.exact }
    }
    fun containsBuiltin(code: String, text: String): Boolean = db.rawQuery(
        "SELECT 1 FROM entries WHERE code=? AND text=? LIMIT 1", arrayOf(code, text)
    ).use { it.moveToFirst() }
    override fun close() = db.close()
}
