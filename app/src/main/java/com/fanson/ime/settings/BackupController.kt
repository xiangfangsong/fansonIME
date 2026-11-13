package com.fanson.ime.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.fanson.ime.R
import com.fanson.ime.data.BackupSection
import com.fanson.ime.data.PersonalBackup
import com.fanson.ime.data.PersonalBackupJson
import com.fanson.ime.data.WubiFrequencyStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** Retained across configuration changes, never placed in saved-instance state or logs. */
class BackupController {
    companion object {
        private const val EXPORT = 4101
        private const val IMPORT = 4102
        private val files = Executors.newSingleThreadExecutor { task -> Thread(task, "personal-backup-files") }
    }
    private val main = Handler(Looper.getMainLooper())
    private var activity: MainActivity? = null
    private var dialog: AlertDialog? = null
    private var stage = "idle"
    private var selection = BackupSection.entries.toSet()
    private var data: PersonalBackup? = null
    private var message = ""
    private var imported = false

    fun attach(owner: MainActivity) { activity = owner; showStage() }
    fun detach(owner: MainActivity) {
        if (activity === owner) {
            dialog?.dismiss(); dialog = null; activity = null
            if (owner.isFinishing && stage != "busy") data = null
        }
    }
    fun export() { if (stage == "idle") { selection = BackupSection.entries.toSet(); stage = "chooseExport"; showStage() } }
    fun import() {
        if (stage != "idle") return
        val owner = activity ?: return
        stage = "waitingImport"
        launch(owner, Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/plain", "application/octet-stream"))
        }, IMPORT)
    }
    private fun launch(owner: Activity, intent: Intent, request: Int) {
        try { owner.startActivityForResult(intent, request) }
        catch (_: android.content.ActivityNotFoundException) { complete("未找到系统文件选择器，请检查系统文件应用") }
    }
    fun result(request: Int, result: Int, intent: Intent?): Boolean {
        if (request != EXPORT && request != IMPORT) return false
        val uri = intent?.data
        if (result != Activity.RESULT_OK || uri == null) { reset(); return true }
        val expected = if (request == EXPORT) "waitingExport" else "waitingImport"
        if (stage != expected) { complete("备份操作已中断，请重新选择文件"); return true }
        if (uri.scheme != "content") { complete("请选择系统文件选择器提供的文件"); return true }
        stage = "busy"; showStage()
        if (request == EXPORT) write(uri) else read(uri)
        return true
    }
    private fun showStage() {
        val owner = activity ?: return
        if (owner.isFinishing || owner.isDestroyed) return
        dialog?.dismiss(); dialog = null
        when (stage) {
            "chooseExport", "chooseImport" -> showSelection(owner, stage == "chooseImport")
            "busy" -> dialog = AlertDialog.Builder(owner).setTitle("正在处理备份")
                .setMessage("正在读取、校验或写入所选内容，请稍候。")
                .setCancelable(false).show()
            "done" -> {
                if (imported) owner.refreshAfterImport()
                imported = false
                dialog = AlertDialog.Builder(owner).setTitle("个人数据备份").setMessage(message)
                    .setPositiveButton("确定") { _, _ -> reset() }.setOnCancelListener { reset() }.show()
            }
        }
    }
    private fun showSelection(owner: MainActivity, importing: Boolean) {
        val density = owner.resources.displayMetrics.density
        fun dp(value: Int) = (value * density + 0.5f).toInt()
        val column = LinearLayout(owner).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(8)) }
        column.addView(TextView(owner).apply {
            text = if (importing) "短语合并去重；emoji 使用次数与最近顺序、五笔词频覆盖还原；设置覆盖文件包含的项。未勾选的内容保持。空记录还原会清空对应的使用记录。"
                else "选择本次导出的内容。emoji 包含次数与最近顺序，用于还原「常用」页。JSON 为明文，请自行保管；不包含剪贴板历史。"
            textSize = 14f
        })
        val boxes = BackupSection.entries.map { section ->
            CheckBox(owner).apply {
                val count = data?.count(section)
                isEnabled = !importing || count != null
                text = when {
                    !importing -> section.label
                    count == null -> owner.getString(R.string.backup_category_missing, section.label)
                    else -> owner.getString(R.string.backup_category_count, section.label, count)
                }
                contentDescription = text
                minHeight = dp(48)
                isChecked = isEnabled && section in selection
                setOnCheckedChangeListener { _, checked ->
                    selection = if (checked) selection + section else selection - section
                    dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = selection.isNotEmpty()
                }
                column.addView(this)
            }
        }
        val scroll = ScrollView(owner).apply { addView(column) }
        dialog = AlertDialog.Builder(owner).setTitle(if (importing) "选择导入内容" else "选择导出内容").setView(scroll)
            .setNegativeButton("取消") { _, _ -> reset() }
            .setPositiveButton(if (importing) "导入所选内容" else "选择保存位置") { _, _ ->
                selection = BackupSection.entries.filterIndexed { index, _ -> boxes[index].isChecked && boxes[index].isEnabled }.toSet()
                if (importing) applyImport() else {
                    stage = "waitingExport"
                    val date = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
                    launch(owner, Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE); type = "application/json"
                        putExtra(Intent.EXTRA_TITLE, "FansonIME-backup-$date.json")
                    }, EXPORT)
                }
            }.setOnCancelListener { reset() }.show()
        dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = selection.isNotEmpty()
    }
    private fun write(uri: Uri) {
        val context = activity?.applicationContext ?: return
        val sections = selection.toSet()
        var snapshot: PersonalBackup? = null
        WubiFrequencyStore.edit(context, { snapshot = it.personalBackup(sections) }) { success ->
            if (!success) { main.post { complete("导出失败，个人数据未修改。请检查数据规模后重试") }; return@edit }
            files.execute {
                val saved = try {
                    val stream = requireNotNull(context.contentResolver.openOutputStream(uri, "wt"))
                    stream.use { PersonalBackupJson.write(requireNotNull(snapshot), it) }; true
                } catch (_: Exception) { false }
                snapshot = null
                main.post { complete(if (saved) "已导出所选内容" else "文件写入未完成，请重新导出；所选位置可能留有不完整文件") }
            }
        }
    }
    private fun read(uri: Uri) {
        val context = activity?.applicationContext ?: return
        files.execute {
            val parsed = try {
                requireNotNull(context.contentResolver.openInputStream(uri)).use(PersonalBackupJson::read)
            } catch (_: Exception) { null }
            main.post {
                if (parsed == null) complete("无法导入：文件不可读、格式或版本不支持、内容损坏或超过限制。个人数据未修改")
                else { data = parsed; selection = parsed.available; stage = "chooseImport"; showStage() }
            }
        }
    }
    private fun applyImport() {
        val context = activity?.applicationContext ?: return
        val selected = requireNotNull(data).select(selection)
        stage = "busy"; showStage()
        WubiFrequencyStore.edit(context, { it.importPersonalBackup(selected) }) { success ->
            main.post {
                imported = success
                complete(if (success) "已导入所选内容；设置将在下次开始输入时生效" else "导入未完成，所选内容未写入，请重试")
            }
        }
    }
    private fun complete(text: String) { data = null; message = text; stage = "done"; showStage() }
    private fun reset() { data = null; stage = "idle"; dialog = null; imported = false }
}
