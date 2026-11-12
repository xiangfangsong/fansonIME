package com.fanson.ime.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.fanson.ime.engine.Candidate
import com.fanson.ime.engine.Lexicon
import com.fanson.ime.engine.PinyinSpelling
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Read-only resource, opened and queried only on the shared Chinese input worker. */
class PinyinRepository(context: Context) : Lexicon, AutoCloseable {
    private val db: SQLiteDatabase
    init {
        val info = JSONObject(context.assets.open("data_info.json").bufferedReader().use { it.readText() })
        val checksum = info.getJSONObject("assets").getString("pinyin.db")
        val file = File(context.noBackupFilesDir, "pinyin-${checksum.take(12)}.db")
        if (!file.exists()) {
            val temp = File(context.noBackupFilesDir, "pinyin.tmp")
            context.assets.open("pinyin.db").use { input -> temp.outputStream().use { input.copyTo(it) } }
            val digest = MessageDigest.getInstance("SHA-256")
            temp.inputStream().use { input ->
                val buffer = ByteArray(16384)
                var count = input.read(buffer)
                while (count >= 0) { digest.update(buffer, 0, count); count = input.read(buffer) }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            check(hash == checksum) { "拼音词库校验失败" }
            check(temp.renameTo(file)) { "无法准备拼音词库" }
        }
        db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
    }

    override fun lookup(code: String): List<Candidate> {
        val key = PinyinSpelling.key(code)
        if (key.isEmpty()) return emptyList()
        val result = mutableListOf<Candidate>()
        val seen = mutableSetOf<String>()
        fun query(sql: String, args: Array<String>, exact: Boolean) {
            db.rawQuery(sql, args).use { cursor ->
                while (cursor.moveToNext()) {
                    val text = cursor.getString(0)
                    if (PinyinSpelling.matchesBoundaries(code, cursor.getString(2)) && seen.add(text))
                        result += Candidate(text, cursor.getString(1), exact)
                }
            }
        }
        query("SELECT text,code,syllables FROM entries WHERE code=? ORDER BY weight DESC,text,syllables", arrayOf(key), true)
        if (result.size < 64) {
            if (key.length <= 3) query("SELECT text,code,syllables FROM completions WHERE prefix=? ORDER BY weight DESC,text,code", arrayOf(key), false)
            else query("SELECT text,code,syllables FROM entries WHERE code>? AND code<? ORDER BY weight DESC,text,code LIMIT 64", arrayOf(key, key + "{"), false)
        }
        return result
    }
    override fun close() = db.close()
}
