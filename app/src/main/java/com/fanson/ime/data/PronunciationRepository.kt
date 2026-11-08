package com.fanson.ime.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.fanson.ime.engine.PronunciationDictionary
import com.fanson.ime.engine.PronunciationLexicon
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Read only, queried exclusively on the IME worker. User input is never stored. */
class PronunciationRepository(context: Context) : PronunciationLexicon, AutoCloseable {
    private val db: SQLiteDatabase
    private val cache = object : LinkedHashMap<String, String>(128, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 512
    }
    private val dictionary = PronunciationDictionary { text ->
        db.rawQuery("SELECT pronunciations FROM readings WHERE text=?", arrayOf(text)).use {
            if (it.moveToFirst()) it.getString(0).split('\n') else emptyList()
        }
    }

    init {
        val info = JSONObject(context.assets.open("data_info.json").bufferedReader().use { it.readText() })
        val checksum = info.getJSONObject("assets").getString("pronunciation.db")
        val file = File(context.noBackupFilesDir, "pronunciation-${checksum.take(12)}.db")
        if (!file.exists()) {
            val temp = File(context.noBackupFilesDir, "pronunciation.tmp")
            context.assets.open("pronunciation.db").use { input -> temp.outputStream().use { input.copyTo(it) } }
            val actual = MessageDigest.getInstance("SHA-256").digest(temp.readBytes()).joinToString("") { "%02x".format(it) }
            check(actual == checksum) { "读音数据校验失败" }
            check(temp.renameTo(file)) { "无法准备读音数据" }
        }
        db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
    }

    override fun reading(text: String): String = cache.getOrPut(text) { dictionary.reading(text) }
    override fun close() { cache.clear(); db.close() }
}
