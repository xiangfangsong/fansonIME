package com.fanson.ime.data

import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import com.fanson.ime.emoji.EmojiUsage
import com.fanson.ime.engine.FrequencyKey
import com.fanson.ime.engine.FrequencyRecord
import com.fanson.ime.engine.InputLanguage
import com.fanson.ime.engine.WubiEnterAction
import com.fanson.ime.engine.WubiFrequency
import java.io.InputStream
import java.io.OutputStream
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.nio.charset.CodingErrorAction

enum class BackupSection(val key: String, val label: String) {
    PHRASES("phrases", "用户自定义短语"), EMOJI("emojiUsage", "emoji 使用次数"),
    FREQUENCY("wubiFrequency", "五笔词频"), SETTINGS("settings", "个性化设置")
}

data class BackupPhrase(val code: String, val text: String)

/** null means absent; an empty collection is an explicitly included empty category. */
data class PersonalBackup(
    val phrases: List<BackupPhrase>? = null,
    val emojiUsage: Map<String, EmojiUsage>? = null,
    val wubiFrequency: List<FrequencyRecord>? = null,
    val settings: Map<String, String>? = null
) {
    fun count(section: BackupSection): Int? = when (section) {
        BackupSection.PHRASES -> phrases?.size
        BackupSection.EMOJI -> emojiUsage?.size
        BackupSection.FREQUENCY -> wubiFrequency?.size
        BackupSection.SETTINGS -> settings?.size
    }
    fun select(sections: Set<BackupSection>) = PersonalBackup(
        phrases.takeIf { BackupSection.PHRASES in sections }, emojiUsage.takeIf { BackupSection.EMOJI in sections },
        wubiFrequency.takeIf { BackupSection.FREQUENCY in sections }, settings.takeIf { BackupSection.SETTINGS in sections }
    )
    val available get() = BackupSection.entries.filter { count(it) != null }.toSet()
}

/** Only intentional user preferences are portable, never arbitrary database keys. */
object BackupPreferences {
    val defaults = linkedMapOf(
        "default_language" to "wubi86", "input_language" to "wubi86", "last_non_english_language" to "",
        "english_auto_capitalize" to "true", "wubi_enter_action" to "commit_code",
        "wubi_frequency" to "true", "auto_unique" to "true", "fifth_commit" to "true",
        "haptic" to "false", "haptic_strength" to "50", "roots" to "false", "number_row" to "true",
        "symbol_auto_return" to "true", "theme" to "system", "key_height" to "48"
    )
    fun valid(key: String, value: String): Boolean = when (key) {
        "default_language", "input_language" -> InputLanguage.entries.any { it.value == value }
        "last_non_english_language" -> value.isEmpty() || InputLanguage.entries.any { it != InputLanguage.ENGLISH && it.value == value }
        "wubi_enter_action" -> WubiEnterAction.entries.any { it.value == value }
        "theme" -> value in setOf("system", "light", "dark")
        "key_height" -> value.toIntOrNull() in 40..64
        "haptic_strength" -> value.toIntOrNull() in 0..100
        else -> key in defaults && value in setOf("true", "false")
    }
}

/** Bounded, strict streaming JSON; no personal values are used in error messages or logs. */
object PersonalBackupJson {
    const val MAX_BYTES = 64L * 1024 * 1024
    const val MAX_PHRASES = 20_000
    const val MAX_EMOJI = 10_000
    const val MAX_FREQUENCY = 20_000
    const val MAX_COUNTER = 1_000_000_000_000L
    private fun code(value: String) = value.length in 1..4 && value.all { it in 'a'..'y' }
    private fun text(value: String, max: Int) = value.isNotBlank() && value.length <= max
    private fun valid(record: FrequencyRecord) = code(record.key.inputCode) && code(record.key.sourceCode) &&
        record.key.sourceCode.startsWith(record.key.inputCode) && text(record.key.sourceText, 200) &&
        text(record.key.text, 200) && record.score.isFinite() && record.score > 0 &&
        record.score <= WubiFrequency.MAX_SCORE && record.updatedAt >= 0

    fun validate(data: PersonalBackup) {
        require(data.available.isNotEmpty())
        data.phrases?.let { rows ->
            require(rows.size <= MAX_PHRASES && rows.all { code(it.code) && text(it.text, 200) })
            require(rows.distinct().size == rows.size)
        }
        data.emojiUsage?.let { rows -> require(rows.size <= MAX_EMOJI && rows.all { (key, value) ->
            text(key, 128) && value.count in 1..MAX_COUNTER && value.lastUsed in 1..MAX_COUNTER
        }) }
        data.wubiFrequency?.let { rows ->
            require(rows.size <= MAX_FREQUENCY && rows.all(::valid))
            require(rows.map { it.key }.toSet().size == rows.size)
        }
        data.settings?.let { rows -> require(rows.size <= 100 && rows.all { (key, value) -> BackupPreferences.valid(key, value) }) }
    }

    fun write(data: PersonalBackup, output: OutputStream) {
        validate(data)
        var size = 0L
        val limited = object : FilterOutputStream(output) {
            override fun write(value: Int) { require(++size <= MAX_BYTES); out.write(value) }
            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                size += length; require(size <= MAX_BYTES); out.write(bytes, offset, length)
            }
        }
        JsonWriter(limited.writer(Charsets.UTF_8)).use { writer ->
            writer.setIndent("  ")
            writer.beginObject().name("format").value("FansonIME").name("version").value(1L)
            data.phrases?.let { rows ->
                writer.name("phrases").beginArray()
                rows.forEach { writer.beginObject().name("code").value(it.code).name("text").value(it.text).endObject() }
                writer.endArray()
            }
            data.emojiUsage?.let { rows ->
                writer.name("emojiUsage").beginArray()
                rows.toSortedMap().forEach { (key, value) ->
                    writer.beginObject().name("text").value(key).name("count").value(value.count)
                        .name("lastUsed").value(value.lastUsed).endObject()
                }
                writer.endArray()
            }
            data.wubiFrequency?.let { rows ->
                writer.name("wubiFrequency").beginArray()
                rows.forEach { row ->
                    writer.beginObject().name("inputCode").value(row.key.inputCode).name("sourceCode").value(row.key.sourceCode)
                        .name("sourceText").value(row.key.sourceText).name("text").value(row.key.text)
                        .name("score").value(row.score).name("updatedAt").value(row.updatedAt).name("custom").value(row.custom).endObject()
                }
                writer.endArray()
            }
            data.settings?.let { rows ->
                writer.name("settings").beginObject()
                rows.toSortedMap().forEach { (key, value) -> writer.name(key).value(value) }
                writer.endObject()
            }
            writer.endObject()
        }
    }

    fun read(input: InputStream): PersonalBackup {
        var size = 0L
        val limited = object : FilterInputStream(input) {
            override fun read(): Int = super.read().also { if (it != -1) require(++size <= MAX_BYTES) }
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int = super.read(bytes, offset, length).also {
                if (it > 0) { size += it; require(size <= MAX_BYTES) }
            }
        }
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        JsonReader(java.io.InputStreamReader(limited, decoder)).use { reader ->
            var format: String? = null; var version: Long? = null
            var phrases: List<BackupPhrase>? = null; var emoji: Map<String, EmojiUsage>? = null
            var frequency: List<FrequencyRecord>? = null; var settings: Map<String, String>? = null
            reader.fields { key -> when (key) {
                "format" -> format = reader.string(40)
                "version" -> version = reader.integer()
                "phrases" -> phrases = reader.rows(MAX_PHRASES) {
                    var code: String? = null; var text: String? = null
                    reader.fields { when (it) { "code" -> code = reader.string(4); "text" -> text = reader.string(200); else -> reader.skipBounded() } }
                    BackupPhrase(requireNotNull(code), requireNotNull(text))
                }
                "emojiUsage" -> {
                    val rows = reader.rows(MAX_EMOJI) {
                        var text: String? = null; var count: Long? = null; var last: Long? = null
                        reader.fields { when (it) {
                            "text" -> text = reader.string(128); "count" -> count = reader.integer()
                            "lastUsed" -> last = reader.integer(); else -> reader.skipBounded()
                        } }
                        requireNotNull(text) to EmojiUsage(requireNotNull(count), requireNotNull(last))
                    }
                    require(rows.map { it.first }.toSet().size == rows.size); emoji = rows.toMap()
                }
                "wubiFrequency" -> frequency = reader.rows(MAX_FREQUENCY) {
                    val strings = HashMap<String, String>(); var score: Double? = null; var updated: Long? = null; var custom: Boolean? = null
                    reader.fields { when (it) {
                        "inputCode", "sourceCode" -> strings[it] = reader.string(4)
                        "sourceText", "text" -> strings[it] = reader.string(200)
                        "score" -> { require(reader.peek() == JsonToken.NUMBER); score = reader.nextDouble() }
                        "updatedAt" -> updated = reader.integer()
                        "custom" -> { require(reader.peek() == JsonToken.BOOLEAN); custom = reader.nextBoolean() }
                        else -> reader.skipBounded()
                    } }
                    FrequencyRecord(FrequencyKey(requireNotNull(strings["inputCode"]), requireNotNull(strings["sourceCode"]),
                        requireNotNull(strings["sourceText"]), requireNotNull(strings["text"])), requireNotNull(score), requireNotNull(updated), requireNotNull(custom))
                }
                "settings" -> {
                    val values = linkedMapOf<String, String>()
                    reader.fields { name ->
                        if (name in BackupPreferences.defaults) values[name] = reader.string(40) else reader.skipBounded()
                    }
                    settings = values
                }
                else -> reader.skipBounded()
            } }
            require(format == "FansonIME" && version == 1L && reader.peek() == JsonToken.END_DOCUMENT)
            return PersonalBackup(phrases, emoji, frequency, settings).also(::validate)
        }
    }
    private fun JsonReader.string(max: Int): String {
        require(peek() == JsonToken.STRING)
        return nextString().also { require(it.length <= max) }
    }
    private fun JsonReader.integer(): Long {
        require(peek() == JsonToken.NUMBER)
        return requireNotNull(nextString().toLongOrNull())
    }
    private fun JsonReader.fields(consume: (String) -> Unit) {
        beginObject(); val seen = HashSet<String>()
        while (hasNext()) {
            val name = nextName(); require(name.length <= 100 && seen.add(name) && seen.size <= 100)
            consume(name)
        }
        endObject()
    }
    private fun <T> JsonReader.rows(max: Int, consume: () -> T): List<T> {
        val rows = ArrayList<T>(); beginArray()
        while (hasNext()) { require(rows.size < max); rows.add(consume()) }
        endArray(); return rows
    }
    private fun JsonReader.skipBounded(depth: Int = 0) {
        require(depth < 16)
        when (peek()) {
            JsonToken.BEGIN_OBJECT -> { beginObject(); while (hasNext()) { nextName(); skipBounded(depth + 1) }; endObject() }
            JsonToken.BEGIN_ARRAY -> { beginArray(); while (hasNext()) skipBounded(depth + 1); endArray() }
            else -> skipValue()
        }
    }
}
