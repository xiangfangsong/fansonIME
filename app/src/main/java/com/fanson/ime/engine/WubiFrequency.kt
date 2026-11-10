package com.fanson.ime.engine

import java.util.TreeSet
import kotlin.math.ln
import kotlin.math.pow

data class FrequencyKey(val inputCode: String, val sourceCode: String, val sourceText: String, val text: String)
data class FrequencyRecord(val key: FrequencyKey, val score: Double, val updatedAt: Long, val custom: Boolean) {
    // Cached once per update: avoid logarithms and boxed Doubles in TreeSet comparisons.
    val priority = ln(score) / ln(2.0) + updatedAt.toDouble() / WubiFrequency.HALF_LIFE_MS
}
data class FrequencyChange(val record: FrequencyRecord?, val revision: Long)
data class FrequencyBatch(val changes: Map<FrequencyKey, FrequencyChange>, val epoch: Long)
data class FrequencyPermit(val allowed: Boolean, val epoch: Long)

/** Shared, bounded state. No Android, disk, event history, timers or periodic decay. */
class WubiFrequency(private val capacity: Int = 20_000, private val trimTo: Int = 18_000) {
    companion object {
        const val HALF_LIFE_MS = 14L * 24 * 60 * 60 * 1000
        const val MAX_SCORE = 16.0
        const val EXPIRED_SCORE = 0.25
        const val RECALL_LIMIT = 64
        fun effective(record: FrequencyRecord, now: Long): Double {
            val age = (now - record.updatedAt).coerceAtLeast(0).toDouble()
            val value = record.score * 2.0.pow(-age / HALF_LIFE_MS)
            return if (value <= EXPIRED_SCORE) 0.0 else value
        }
        fun key(inputCode: String, candidate: Candidate) = FrequencyKey(inputCode, candidate.code, candidate.sourceText, candidate.text)
        private fun validCode(code: String) = code.length in 1..4 && code.all { it in 'a'..'y' }
        private val order = Comparator<FrequencyRecord> { left, right ->
            var result = left.priority.compareTo(right.priority)
            if (result == 0) result = left.key.inputCode.compareTo(right.key.inputCode)
            if (result == 0) result = left.key.sourceCode.compareTo(right.key.sourceCode)
            if (result == 0) result = left.key.sourceText.compareTo(right.key.sourceText)
            if (result == 0) result = left.key.text.compareTo(right.key.text)
            result
        }
    }
    init { require(capacity > 0 && trimTo in 1..capacity) }
    private val records = HashMap<FrequencyKey, FrequencyRecord>()
    private val byInput = HashMap<String, TreeSet<FrequencyRecord>>()
    private val eviction = TreeSet(order)
    // Only rows known to be on disk (or in-flight) need deletion tombstones.
    private val knownOnDisk = HashSet<FrequencyKey>()
    private var inFlight: FrequencyBatch? = null
    private val dirty = HashMap<FrequencyKey, FrequencyChange>()
    private var revision = 0L
    private var reachedCapacity = false
    @get:Synchronized var epoch = 0L
        private set
    @get:Synchronized val size get() = records.size
    @get:Synchronized val pendingSize get() = dirty.size

    @Synchronized fun load(values: List<FrequencyRecord>, now: Long) {
        check(records.isEmpty() && dirty.isEmpty())
        values.forEach { value ->
            knownOnDisk.add(value.key)
            if (!validCode(value.key.inputCode) || !validCode(value.key.sourceCode) ||
                !value.key.sourceCode.startsWith(value.key.inputCode) ||
                value.key.sourceText.isBlank() || value.key.sourceText.length > 200 ||
                value.key.text.isBlank() || value.key.text.length > 200 ||
                !value.score.isFinite() || value.score <= 0 || value.updatedAt < 0) {
                dirty[value.key] = FrequencyChange(null, ++revision)
            } else {
                val corrected = value.copy(score = value.score.coerceAtMost(MAX_SCORE), updatedAt = value.updatedAt.coerceAtMost(now))
                if (effective(corrected, now) == 0.0) dirty[value.key] = FrequencyChange(null, ++revision)
                else {
                    insert(corrected)
                    if (corrected != value) dirty[value.key] = FrequencyChange(corrected, ++revision)
                }
            }
        }
        while (records.size > capacity) remove(eviction.first())
    }

    private fun insert(value: FrequencyRecord) {
        records.put(value.key, value)?.let { old ->
            eviction.remove(old); byInput[old.key.inputCode]?.remove(old)
        }
        eviction.add(value)
        byInput.getOrPut(value.key.inputCode) { TreeSet(order) }.add(value)
    }
    private fun remove(value: FrequencyRecord) {
        records.remove(value.key); eviction.remove(value)
        byInput[value.key.inputCode]?.let { entries ->
            entries.remove(value)
            if (entries.isEmpty()) byInput.remove(value.key.inputCode)
        }
        if (value.key in knownOnDisk || inFlight?.changes?.get(value.key)?.record != null) dirty[value.key] = FrequencyChange(null, ++revision)
        else dirty.remove(value.key)
    }

    /** Called only after the corresponding platform commit accepted the candidate. */
    @Synchronized fun record(inputCode: String, candidate: Candidate, expectedEpoch: Long, now: Long): Boolean {
        if (expectedEpoch != epoch || !validCode(inputCode) || !validCode(candidate.code) ||
            !candidate.code.startsWith(inputCode) || candidate.text.isBlank() || candidate.text.length > 200 ||
            candidate.sourceText.isBlank() || candidate.sourceText.length > 200) return false
        val key = key(inputCode, candidate)
        val score = records[key]?.let { effective(it, now) } ?: 0.0
        val value = FrequencyRecord(key, (score + 1).coerceAtMost(MAX_SCORE), now, candidate.custom)
        // Admit the new habit even when all old rows have high scores. Evicting
        // after insertion could evict this new row forever, so it could never learn.
        if (key !in records && records.size >= capacity) {
            reachedCapacity = true
            remove(eviction.first())
        }
        insert(value)
        dirty[key] = FrequencyChange(value, ++revision)
        if (records.size >= capacity) reachedCapacity = true
        while (records.size > capacity) remove(eviction.first())
        return true
    }
    fun accept(result: EngineResult, index: Int, accepted: Boolean, permit: FrequencyPermit, now: Long): Boolean {
        if (!accepted || !permit.allowed) return false
        val selection = result.selections.firstOrNull { it.commitIndex == index } ?: return false
        if (result.commits.getOrNull(index) != selection.candidate.text) return false
        return record(selection.inputCode, selection.candidate, permit.epoch, now)
    }

    @Synchronized fun rank(inputCode: String, candidates: List<Candidate>, now: Long): List<Candidate> {
        if (candidates.size < 2 || byInput[inputCode].isNullOrEmpty()) return candidates
        data class Scored(val candidate: Candidate, val tier: Int, val score: Double, val index: Int)
        return candidates.mapIndexed { index, candidate ->
            val score = records[key(inputCode, candidate)]?.let { effective(it, now) } ?: 0.0
            Scored(candidate, if (candidate.custom) 0 else if (candidate.exact) 1 else 2, score, index)
        }.sortedWith(compareBy<Scored> { it.tier }.thenByDescending { it.score }.thenBy { it.index }).map { it.candidate }
    }

    /** Source candidates must still be validated against the current dictionary. */
    @Synchronized fun recall(inputCode: String, now: Long): List<Candidate> {
        val result = ArrayList<Candidate>(RECALL_LIMIT)
        val seen = HashSet<Pair<String, String>>()
        val iterator = byInput[inputCode]?.descendingIterator() ?: return result
        while (iterator.hasNext() && result.size < RECALL_LIMIT) {
            val value = iterator.next()
            if (effective(value, now) == 0.0) break
            if (!value.custom && seen.add(value.key.sourceCode to value.key.sourceText)) {
                result.add(Candidate(value.key.sourceText, value.key.sourceCode, value.key.sourceCode == inputCode))
            }
        }
        return result
    }

    fun prepareSave(now: Long, onlySources: Set<Pair<String, String>>? = null): FrequencyBatch {
        if (onlySources == null) compact(now)
        return synchronized(this) {
            val changes = if (onlySources == null) HashMap(dirty) else dirty.filterKeys { (it.sourceCode to it.sourceText) in onlySources }
            FrequencyBatch(changes, epoch).also { inFlight = it }
        }
    }
    private fun compact(now: Long) {
        val target = synchronized(this) {
            val value = if (reachedCapacity) trimTo else capacity
            reachedCapacity = false
            value
        }
        var budget = capacity
        // Background maintenance yields the monitor every 128 rows. Reopening
        // while it runs must not wait for a whole 20k-row expiration sweep.
        while (budget > 0) {
            val removed = synchronized(this) {
                var count = 0
                while (count < minOf(128, budget) && eviction.isNotEmpty()) {
                    val first = eviction.first()
                    if (records.size <= target && effective(first, now) > 0.0) break
                    remove(first); count++
                }
                count
            }
            if (removed == 0) break
            budget -= removed
            Thread.yield() // Event-triggered work only; no scheduled retry or delay.
        }
    }
    fun acknowledge(batch: FrequencyBatch) {
        val iterator = batch.changes.iterator()
        while (iterator.hasNext()) {
            synchronized(this) {
                if (batch.epoch != epoch) return
                repeat(128) {
                    if (iterator.hasNext()) {
                        val (key, change) = iterator.next()
                        if (dirty[key]?.revision == change.revision) dirty.remove(key)
                        if (change.record == null) knownOnDisk.remove(key) else knownOnDisk.add(key)
                    }
                }
            }
            Thread.yield()
        }
        synchronized(this) { if (inFlight === batch) inFlight = null }
    }
    /** A rolled-back transaction must not leave phantom disk keys/tombstones forever. */
    @Synchronized fun failed(batch: FrequencyBatch) {
        if (inFlight !== batch || batch.epoch != epoch) return
        inFlight = null
        batch.changes.forEach { (key, _) ->
            if (key !in knownOnDisk && key !in records && dirty[key]?.record == null) dirty.remove(key)
        }
    }
    @Synchronized fun invalidateFeedback() { epoch++ }
    @Synchronized fun clear() {
        epoch++
        records.clear(); byInput.clear(); eviction.clear(); dirty.clear(); knownOnDisk.clear(); inFlight = null
        reachedCapacity = false
    }
    /** Preserve learned rows when an identical built-in entry still exists. */
    @Synchronized fun removePhrase(code: String, text: String, hasBuiltin: Boolean) {
        removePhrases(mapOf((code to text) to hasBuiltin))
    }
    @Synchronized fun removePhrases(sources: Map<Pair<String, String>, Boolean>) {
        if (sources.isEmpty()) return
        epoch++ // reject feedback from candidates selected before this explicit deletion
        val affected = records.values.filter { it.custom && (it.key.sourceCode to it.key.sourceText) in sources }
        affected.forEach {
            if (sources[it.key.sourceCode to it.key.sourceText] == true) {
                val replacement = it.copy(custom = false)
                insert(replacement); dirty[it.key] = FrequencyChange(replacement, ++revision)
            } else remove(it)
        }
    }
}
