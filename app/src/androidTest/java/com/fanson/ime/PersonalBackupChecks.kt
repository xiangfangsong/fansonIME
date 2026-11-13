package com.fanson.ime

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import com.fanson.ime.data.*
import com.fanson.ime.emoji.EmojiRepository
import com.fanson.ime.emoji.EmojiUsage
import com.fanson.ime.engine.*
import com.fanson.ime.settings.MainActivity
import java.io.ByteArrayOutputStream
import java.io.File

/** Only the designated isolated emulator; all payloads are synthetic. */
object PersonalBackupChecks {
    fun run(test: Instrumentation): List<String> {
        val passed = object : ArrayList<String>() {
            override fun add(element: String): Boolean {
                test.sendStatus(0, Bundle().apply { putString("backup_check", element) })
                return super.add(element)
            }
        }
        val now = System.currentTimeMillis()
        val frequency = FrequencyRecord(FrequencyKey("a", "aaaa", "合成源词", "合成源词"), 4.0, now, false)
        val all = PersonalBackup(listOf(BackupPhrase("abc", "合成\"短语\n😀")),
            mapOf("😀" to EmojiUsage(9, 10), "😂" to EmojiUsage(9, 11)), listOf(frequency), BackupPreferences.defaults + ("theme" to "dark"))
        fun encode(value: PersonalBackup) = ByteArrayOutputStream().also { PersonalBackupJson.write(value, it) }.toByteArray()
        fun read(value: String) = PersonalBackupJson.read(value.byteInputStream())
        check(PersonalBackupJson.read(encode(all).inputStream()) == all)
        passed += "backup_json_four_categories_unicode_newlines_roundtrip"
        for (section in BackupSection.entries) {
            val selected = all.select(setOf(section))
            check(PersonalBackupJson.read(encode(selected).inputStream()) == selected)
            check(selected.available == setOf(section))
        }
        passed += "backup_json_selective_export_omits_unselected_categories"
        val empty = PersonalBackup(emptyList(), emptyMap(), emptyList(), emptyMap())
        check(PersonalBackupJson.read(encode(empty).inputStream()).available.size == 4)
        check(read("""{"format":"FansonIME","version":1,"phrases":[]}""").count(BackupSection.EMOJI) == null)
        passed += "backup_json_missing_and_explicit_empty_categories_are_distinct"
        val bad = listOf(
            """{"format":"FansonIME","version":2,"phrases":[]}""",
            """{"format":"FansonIME","version":1,"phrases":null}""",
            """{"format":"FansonIME","version":1,"phrases":[],"phrases":[]}""",
            """{"format":"FansonIME","version":1,"phrases":[{"code":"z","text":"合成"}]}""",
            """{"format":"FansonIME","version":1,"emojiUsage":[{"text":"😀","count":-1,"lastUsed":1}]}""",
            """{"format":"FansonIME","version":1,"settings":{"key_height":"999"}}""",
            """{"format":"FansonIME","version":1,"settings":{"haptic":"yes"}}""",
            """{"format":"FansonIME","version":1,"settings":{"theme":true}}""",
            """{"format":"FansonIME","version":1,"phrases":[]} {}""",
            """{"format":"FansonIME","version":1,"phrases":[""",
            """{"format":"FansonIME","version":1,"phrases":[],"unknown":""" + "[".repeat(20) + "0" + "]".repeat(20) + "}",
            """{"format":"FansonIME","version":1}"""
        )
        bad.forEach { raw -> check(runCatching { read(raw) }.isFailure) }
        check(runCatching { PersonalBackupJson.read(byteArrayOf(0xc3.toByte(), 0x28).inputStream()) }.isFailure)
        check(runCatching { encode(all.copy(wubiFrequency = listOf(frequency.copy(score = Double.POSITIVE_INFINITY)))) }.isFailure)
        check(runCatching { encode(all.copy(wubiFrequency = List(20_001) { frequency })) }.isFailure)
        check(runCatching { encode(all.copy(emojiUsage = mapOf("😀" to EmojiUsage(Long.MAX_VALUE, 1)))) }.isFailure)
        val oversized = object : java.io.InputStream() {
            var remaining = PersonalBackupJson.MAX_BYTES + 1
            override fun read(): Int = if (remaining-- > 0) 32 else -1
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                if (remaining <= 0) return -1
                val size = minOf(length.toLong(), remaining).toInt()
                bytes.fill(32, offset, offset + size); remaining -= size
                return size
            }
        }
        check(runCatching { PersonalBackupJson.read(oversized) }.isFailure)
        passed += "backup_json_rejects_malformed_duplicate_future_version_types_depth_ranges_and_limits"
        val unknown = read("""{"format":"FansonIME","version":1,"settings":{"theme":"dark","unrecognized":"ignored"}}""")
        check(unknown.settings == mapOf("theme" to "dark"))
        passed += "backup_settings_allowlist_ignores_unknown_keys"
        val fixture = File(test.targetContext.cacheDir, "personal-backup-synthetic.db")
        SQLiteDatabase.deleteDatabase(fixture)
        try {
            LocalStore(fixture).use { store ->
                store.addPhrase("b", "原有合成短语"); store.recordEmojiUsage("👍"); store.put("theme", "light")
                store.recordClipboard("合成剪贴板不导出")
                store.importPersonalBackup(all)
                store.importPersonalBackup(all)
                check(store.phrases().size == 2)
                check(store.emojiUsage() == all.emojiUsage)
                check(store.frequencyRecords() == all.wubiFrequency)
                check(store.get("theme", "") == "dark" && store.clipboardHistory().single().text == "合成剪贴板不导出")
                val emojis = EmojiRepository(test.targetContext)
                check(emojis.filter("popular", "", store.emojiUsage()).first().text == "😂")
                passed += "backup_import_merges_phrases_idempotently_restores_emoji_order_frequency_settings_keeps_clipboard"
                for (section in BackupSection.entries) {
                    val before = store.personalBackup(BackupSection.entries.toSet(), store.frequencyRecords())
                    store.importPersonalBackup(empty.select(setOf(section)))
                    val after = store.personalBackup(BackupSection.entries.toSet(), store.frequencyRecords())
                    check(before.select(BackupSection.entries.toSet() - section) == after.select(BackupSection.entries.toSet() - section))
                    store.importPersonalBackup(all)
                }
                passed += "backup_import_each_category_leaves_unselected_data_unchanged"
                SQLiteDatabase.openDatabase(fixture.path, null, SQLiteDatabase.OPEN_READWRITE).use { raw ->
                    raw.execSQL("CREATE TRIGGER reject_backup_emoji BEFORE INSERT ON emoji_usage BEGIN SELECT RAISE(ABORT,'synthetic failure'); END")
                }
                val before = store.personalBackup(BackupSection.entries.toSet(), store.frequencyRecords())
                check(runCatching { store.importPersonalBackup(all.copy(phrases = listOf(BackupPhrase("c", "事务回滚合成")))) }.isFailure)
                check(store.personalBackup(BackupSection.entries.toSet(), store.frequencyRecords()) == before)
                passed += "backup_import_sql_failure_rolls_back_all_categories"
            }
        } finally { SQLiteDatabase.deleteDatabase(fixture) }
        var original: PersonalBackup? = null
        WubiFrequencyChecks.edit(test) { shared ->
            original = shared.personalBackup(BackupSection.entries.toSet())
            shared.clearFrequency()
            val candidate = Candidate("未落盘的合成学习", "a")
            shared.model.record("a", candidate, shared.model.epoch, now)
            val snapshot = shared.personalBackup(setOf(BackupSection.FREQUENCY))
            check(snapshot.wubiFrequency?.single()?.key?.text == candidate.text)
            val oldEpoch = shared.model.epoch
            shared.importPersonalBackup(all.select(setOf(BackupSection.FREQUENCY)))
            check(shared.model.snapshot(now) == listOf(frequency))
            check(!shared.model.record("a", candidate, oldEpoch, now))
            shared.saveOnHidden()
        }
        WubiFrequencyChecks.edit(test) { shared ->
            LocalStore(test.targetContext).use { check(it.frequencyRecords() == listOf(frequency)) }
            shared.importPersonalBackup(requireNotNull(original))
        }
        passed += "backup_live_unsaved_frequency_export_restore_epoch_and_no_queued_old_save_resurrection"
        ui(test, all, passed)
        return passed
    }

    private fun ui(test: Instrumentation, payload: PersonalBackup, passed: MutableList<String>) {
        fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
        fun roots() = WindowInspector.getGlobalWindowViews().flatMap(::views)
        fun button(label: String) = roots().filterIsInstance<Button>().first { it.text.toString() == label }
        fun click(label: String) { test.runOnMainSync { button(label).performClick() }; test.waitForIdleSync() }
        fun await(predicate: () -> Boolean) {
            val deadline = SystemClock.uptimeMillis() + 15_000
            while (SystemClock.uptimeMillis() < deadline) {
                var ready = false; test.runOnMainSync { ready = predicate() }
                if (ready) return
                SystemClock.sleep(50)
            }
            error("Backup UI condition timed out")
        }
        var activity = test.startActivitySync(Intent(test.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        test.waitForIdleSync()
        click("导出 JSON 备份")
        test.runOnMainSync {
            val boxes = roots().filterIsInstance<CheckBox>()
            check(boxes.size == 4 && boxes.all { it.isEnabled && it.isChecked })
            boxes.forEach { it.isChecked = false }
            check(!button("选择保存位置").isEnabled)
            boxes.first().isChecked = true
            check(button("选择保存位置").isEnabled)
        }
        click("取消")
        passed += "backup_export_selection_four_categories_and_empty_selection_disabled_ui"
        // Real platform document picker launch and cancellation, without granting any permission.
        click("导入 JSON 备份")
        val deadline = SystemClock.uptimeMillis() + 15_000
        var picker = false
        while (SystemClock.uptimeMillis() < deadline) {
            val root = test.uiAutomation.rootInActiveWindow
            if (root?.packageName?.toString()?.contains("documentsui") == true) { picker = true; break }
            SystemClock.sleep(100)
        }
        check(picker)
        check(test.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
        val backDeadline = SystemClock.uptimeMillis() + 10_000
        while (test.uiAutomation.rootInActiveWindow?.packageName?.toString()?.contains("documentsui") == true && SystemClock.uptimeMillis() < backDeadline) {
            SystemClock.sleep(300)
            test.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        }
        check(test.uiAutomation.rootInActiveWindow?.packageName?.toString() == test.targetContext.packageName)
        passed += "backup_real_system_document_picker_opens_and_cancels_without_permissions"

        val resolver = test.targetContext.contentResolver
        val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "FansonIME-synthetic-backup-check.json")
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }))
        try {
            requireNotNull(resolver.openOutputStream(uri)).use { PersonalBackupJson.write(payload.select(setOf(BackupSection.PHRASES)), it) }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            var captured: Intent? = null
            val monitor = object : Instrumentation.ActivityMonitor() {
                override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                    if (intent.action == Intent.ACTION_OPEN_DOCUMENT || intent.action == Intent.ACTION_CREATE_DOCUMENT) {
                        captured = intent
                        return Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri))
                    }
                    return null
                }
            }
            test.addMonitor(monitor)
            try {
                click("导入 JSON 备份")
                await { roots().filterIsInstance<CheckBox>().size == 4 }
                check(captured?.action == Intent.ACTION_OPEN_DOCUMENT && captured?.hasCategory(Intent.CATEGORY_OPENABLE) == true)
                test.runOnMainSync {
                    val boxes = roots().filterIsInstance<CheckBox>()
                    check(boxes.first().isEnabled && boxes.first().isChecked)
                    check(boxes.drop(1).all { !it.isEnabled && !it.isChecked && it.text.contains("文件未包含") })
                    boxes.first().isChecked = false
                    check(!button("导入所选内容").isEnabled)
                }
                val rotation = test.addMonitor(MainActivity::class.java.name, null, false)
                try {
                    test.runOnMainSync { activity.recreate() }
                    activity = requireNotNull(test.waitForMonitorWithTimeout(rotation, 10_000))
                    await { roots().filterIsInstance<CheckBox>().size == 4 }
                    test.runOnMainSync {
                        val boxes = roots().filterIsInstance<CheckBox>()
                        check(!boxes.first().isChecked && boxes.first().isEnabled)
                        check(boxes.drop(1).all { !it.isEnabled && !it.isChecked })
                        check(!button("导入所选内容").isEnabled)
                    }
                } finally { test.removeMonitor(rotation) }
                click("取消")
                passed += "backup_import_missing_categories_disabled_unchecked_and_selection_required_ui"
                passed += "backup_activity_recreation_preserves_import_payload_selection_and_disabled_categories"
                requireNotNull(resolver.openOutputStream(uri, "wt")).use { PersonalBackupJson.write(PersonalBackup(emojiUsage = emptyMap()), it) }
                click("导入 JSON 备份")
                await { roots().filterIsInstance<CheckBox>().size == 4 }
                test.runOnMainSync {
                    val box = roots().filterIsInstance<CheckBox>()[1]
                    check(box.isEnabled && box.isChecked && box.text.contains("0 项"))
                }
                click("取消")
                passed += "backup_import_empty_category_remains_selectable_ui"
                click("导出 JSON 备份")
                test.runOnMainSync { roots().filterIsInstance<CheckBox>().drop(1).forEach { it.isChecked = false } }
                click("选择保存位置")
                await { roots().filterIsInstance<TextView>().any { it.text.toString() == "已导出所选内容" } }
                check(captured?.action == Intent.ACTION_CREATE_DOCUMENT && captured?.type == "application/json")
                val restored = requireNotNull(resolver.openInputStream(uri)).use(PersonalBackupJson::read)
                check(restored.available == setOf(BackupSection.PHRASES))
                click("确定")
                passed += "backup_export_create_document_result_writes_only_checked_category_ui"
            } finally { test.removeMonitor(monitor) }
            // Actual SAF save/open, including the URI grants delivered by DocumentsUI.
            val name = "FansonIME-synthetic-saf-${SystemClock.uptimeMillis()}.json"
            val phrase = BackupPhrase("ab", "合成系统选择器往返短语")
            LocalStore(test.targetContext).use { it.addPhrase(phrase.code, phrase.text) }
            fun nodes(node: android.view.accessibility.AccessibilityNodeInfo?): List<android.view.accessibility.AccessibilityNodeInfo> =
                if (node == null) emptyList() else listOf(node) + (0 until node.childCount).flatMap { nodes(node.getChild(it)) }
            fun systemNode(predicate: (android.view.accessibility.AccessibilityNodeInfo) -> Boolean): android.view.accessibility.AccessibilityNodeInfo {
                val end = SystemClock.uptimeMillis() + 15_000
                while (SystemClock.uptimeMillis() < end) {
                    nodes(test.uiAutomation.rootInActiveWindow).firstOrNull(predicate)?.let { return it }
                    SystemClock.sleep(100)
                }
                error("Synthetic SAF node missing")
            }
            fun systemClick(node: android.view.accessibility.AccessibilityNodeInfo) {
                var target = node
                while (!target.isClickable && target.parent != null) target = target.parent
                check(target.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
            }
            try {
                click("导出 JSON 备份")
                test.runOnMainSync { roots().filterIsInstance<CheckBox>().drop(1).forEach { it.isChecked = false } }
                click("选择保存位置")
                val filename = systemNode { it.className?.toString() == "android.widget.EditText" }
                check(filename.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                    putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, name)
                }))
                systemClick(systemNode { it.text?.toString()?.equals("Save", ignoreCase = true) == true && it.isEnabled })
                await { roots().filterIsInstance<TextView>().any { it.text.toString() == "已导出所选内容" } }
                click("确定")
                LocalStore(test.targetContext).use { it.removePhrase(phrase.code, phrase.text) }
                click("导入 JSON 备份")
                systemClick(systemNode { it.text?.toString() == name })
                await { roots().filterIsInstance<CheckBox>().let { boxes -> boxes.size == 4 && boxes.all { it.hasWindowFocus() && it.isShown } } }
                SystemClock.sleep(250) // Wait for the displayed dialog frame, not just attached Views.
                val screenshot = test.uiAutomation.takeScreenshot()
                if (screenshot != null) {
                    val folder = File(test.targetContext.externalCacheDir, "qa").apply { mkdirs() }
                    File(folder, "54-backup-missing-categories-0.5.1.png").outputStream().use {
                        check(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
                    }
                    screenshot.recycle()
                }
                // A successful import is performed through the actual confirmation UI.
                click("导入所选内容")
                await { roots().filterIsInstance<TextView>().any { it.text.toString().startsWith("已导入所选内容") } }
                LocalStore(test.targetContext).use { store -> check(store.phrases().any { it.code == phrase.code && it.text == phrase.text }) }
                click("确定")
                passed += "backup_real_saf_save_then_open_json_and_confirm_import_without_permissions"
            } finally {
                LocalStore(test.targetContext).use { it.removePhrase(phrase.code, phrase.text) }
                // Delete only this test's unique synthetic export, never any pre-existing file.
                resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Downloads._ID),
                    "${MediaStore.Downloads.DISPLAY_NAME}=?", arrayOf(name), null)?.use { cursor ->
                    while (cursor.moveToNext()) resolver.delete(android.content.ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cursor.getLong(0)), null, null)
                }
            }
        } finally { resolver.delete(uri, null, null); test.runOnMainSync { activity.finish() } }
        val permissions = test.targetContext.packageManager.getPackageInfo(test.targetContext.packageName, android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet()
        check(permissions == setOf("android.permission.VIBRATE"))
        passed += "backup_installed_manifest_has_only_vibrate_no_storage_network_or_camera_permission"
    }
}
