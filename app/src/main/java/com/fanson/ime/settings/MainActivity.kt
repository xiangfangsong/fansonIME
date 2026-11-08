package com.fanson.ime.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.*
import com.fanson.ime.data.LocalStore
import com.fanson.ime.engine.WubiEnterAction
import com.fanson.ime.R
import com.fanson.ime.keyboard.Palette
import com.fanson.ime.keyboard.Ui
import com.fanson.ime.keyboard.dp

class MainActivity : Activity() {
    private lateinit var store: LocalStore
    private lateinit var ui: Ui
    private lateinit var page: LinearLayout
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = LocalStore(this)
        buildPage()
    }

    private fun buildPage() {
        ui = Ui(this, Palette.of(this, store.get("theme", "system")))
        window.statusBarColor = ui.palette.surface
        window.navigationBarColor = ui.palette.surface
        val scroll = ScrollView(this).apply { setBackgroundColor(ui.palette.surface); isFillViewport = true }
        page = ui.column().apply { setPadding(dp(18), dp(18), dp(18), dp(24)) }
        scroll.addView(page)
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(scroll)
        page.addView(ui.text("放松 / 个人输入法", 12f, ui.palette.accent))
        page.addView(ui.title(getString(R.string.app_name), 34f))
        page.addView(ui.text("五笔86 · 全部离线 · 自己掌控", 16f, ui.palette.muted))
        status = ui.text("", 14f, ui.palette.accent)
        page.addView(status)
        val start = ui.row()
        ui.addWeighted(start, ui.button("① 启用输入法", true) { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) })
        ui.addWeighted(start, ui.button("② 选择输入法") { getSystemService(InputMethodManager::class.java).showInputMethodPicker() })
        page.addView(start)
        section("本地试打")
        val sample = EditText(this).apply {
            hint = "点这里试试五笔、emoji 和剪贴板"
            textSize = 17f; setTextColor(ui.palette.ink); setHintTextColor(ui.palette.muted)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            gravity = Gravity.TOP or Gravity.START
            minLines = 3; maxLines = 6; setPadding(dp(12), dp(8), dp(12), dp(8))
            background = ui.round(ui.palette.key, border = true)
            isSaveEnabled = false
        }
        page.addView(sample, LinearLayout.LayoutParams(-1, dp(120)))
        page.addView(ui.text("试打内容不保存。剪贴板查看和清空在键盘顶部的「剪贴板」面板中。", 13f, ui.palette.muted))
        section("输入习惯")
        toggle("四码唯一自动上屏", "auto_unique", true)
        toggle("第五码提交首选并开始新编码", "fifth_commit", true)
        val enterRow = ui.row()
        enterRow.addView(ui.text("回车键功能"), LinearLayout.LayoutParams(0, dp(48), 1f))
        val enterActions = WubiEnterAction.entries
        val enterSpinner = Spinner(this).apply {
            contentDescription = "回车键功能"
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, enterActions.map { it.label })
            setSelection(enterActions.indexOf(WubiEnterAction.fromPreference(store.get("wubi_enter_action", "commit_code"))))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    store.put("wubi_enter_action", enterActions[position].value)
                }
            }
        }
        enterRow.addView(enterSpinner); page.addView(enterRow)
        page.addView(ui.text("有五笔编码时生效；无编码时仍按输入框要求换行或执行发送等操作。首个候选无匹配时保留编码。", 13f, ui.palette.muted))
        toggle("按键轻震动", "haptic", false)
        toggle("字母键显示字根提示", "roots", false)
        section("键盘外观")
        val themeRow = ui.row()
        themeRow.addView(ui.text("主题"), LinearLayout.LayoutParams(0, dp(48), 1f))
        val themeValues = listOf("system", "light", "dark")
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("跟随系统", "浅色", "深色"))
        spinner.setSelection(themeValues.indexOf(store.get("theme", "system")).coerceAtLeast(0))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (store.get("theme", "system") != themeValues[position]) { store.put("theme", themeValues[position]); buildPage() }
            }
        }
        themeRow.addView(spinner); page.addView(themeRow)
        val heightText = ui.text("")
        val height = SeekBar(this).apply { min = 40; max = 64; progress = store.get("key_height", "48").toIntOrNull()?.coerceIn(40, 64) ?: 48 }
        heightText.text = getString(R.string.key_height_value, height.progress)
        height.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) { store.put("key_height", seekBar.progress.toString()) }
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { heightText.text = getString(R.string.key_height_value, progress) }
        })
        page.addView(heightText); page.addView(height)
        section("自己的常用短语")
        page.addView(ui.text("手动添加编码和短语，不自动记录输入历史。自定义短语在相同编码下优先。", 13f, ui.palette.muted))
        val phraseButtons = ui.row()
        ui.addWeighted(phraseButtons, ui.button("添加短语") { addPhrase() })
        ui.addWeighted(phraseButtons, ui.button("管理短语") { managePhrases() })
        page.addView(phraseButtons)
        section("隐私与数据")
        page.addView(ui.text("安装包无网络权限；无账号、广告、统计或云词库。不学习字词频率、不保存输入正文历史。仅在本机保存 emoji 使用次数与最近顺序，供单页常用排序；剪贴板记录最近 10 项普通文本。所有个人数据均排除系统备份。", 15f))
        page.addView(ui.text("输入法作为系统默认输入法且服务运行时记录后续复制内容，标记为敏感的内容不存入历史。剪贴板「一键清空」同时清除系统当前内容和本输入法历史，其他应用的历史需分别管理。", 13f, ui.palette.muted))
        val clear = ui.button("清空手动短语和 emoji 使用次数") {
            AlertDialog.Builder(this).setTitle("清空本地个人数据？").setMessage("会删除全部手动短语与 emoji 使用次数，键盘设置和剪贴板历史保留。")
                .setNegativeButton("取消", null).setPositiveButton("清空") { _, _ -> store.clearPersonalData(); toast("已清空短语和 emoji 次数") }.show()
        }
        page.addView(clear, LinearLayout.LayoutParams(-1, dp(48)))
        section("关于")
        page.addView(ui.text("放松输入法 0.1.7 · Android 12+\n五笔码表：Rime wubi86 / LGPL-3.0\nEmoji：Unicode 16.0 / CLDR 46\n简繁：OpenCC 1.1.9 / Apache-2.0\n英文词表：FrequencyWords 2018 / CC BY-SA 4.0", 13f, ui.palette.muted))
        page.addView(ui.button("第三方数据与许可") { showLicenses() }, LinearLayout.LayoutParams(-1, dp(48)))
        refreshStatus()
    }

    private fun section(label: String) { page.addView(ui.title(label, 19f).apply { setPadding(dp(8), dp(24), dp(8), dp(8)) }) }
    private fun toggle(label: String, key: String, default: Boolean) {
        val toggle = Switch(this).apply {
            text = label; textSize = 15f; setTextColor(ui.palette.ink); minHeight = dp(52)
            isChecked = store.get(key, default.toString()).toBoolean()
            setOnCheckedChangeListener { _, checked -> store.put(key, checked.toString()) }
        }
        page.addView(toggle)
    }
    private fun addPhrase() {
        val content = ui.column().apply { setPadding(dp(20), dp(8), dp(20), dp(8)) }
        val code = EditText(this).apply { hint = "五笔编码（a-y，1～4 码）"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD; isSaveEnabled = false }
        val text = EditText(this).apply { hint = "要输入的短语（最多 200 字符）"; isSaveEnabled = false }
        content.addView(code); content.addView(text)
        val dialog = AlertDialog.Builder(this).setTitle("添加常用短语").setView(content).setNegativeButton("取消", null).setPositiveButton("保存", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val typedCode = code.text.toString().lowercase().trim()
            val phrase = text.text.toString().trim()
            if (!typedCode.matches(Regex("[a-y]{1,4}"))) code.error = "请输入 1～4 个 a-y 字母"
            else if (phrase.isEmpty() || phrase.length > 200) text.error = "请输入 1～200 字符的短语"
            else { store.addPhrase(typedCode, phrase); toast("短语已保存"); dialog.dismiss() }
        } }
        dialog.show()
    }
    private fun managePhrases() {
        val phrases = store.phrases()
        if (phrases.isEmpty()) { toast("还没有手动短语"); return }
        AlertDialog.Builder(this).setTitle("点击删除短语").setItems(phrases.map { "${it.code}  ${it.text}" }.toTypedArray()) { _, i ->
            val phrase = phrases[i]
            AlertDialog.Builder(this).setTitle("删除此短语？").setMessage(phrase.text).setNegativeButton("取消", null)
                .setPositiveButton("删除") { _, _ -> store.removePhrase(phrase.code, phrase.text); toast("已删除") }.show()
        }.setNegativeButton("关闭", null).show()
    }
    private fun showLicenses() {
        val names = arrayOf("RIME-AUTHORS.txt", "RIME-LGPL-3.0.txt", "GPL-3.0.txt", "UNICODE-LICENSE.txt", "COMPLETION-NOTICE.txt", "OPENCC-AUTHORS.txt", "OPENCC-LICENSE.txt", "CC-BY-SA-4.0.txt", "FREQUENCYWORDS-README.md")
        AlertDialog.Builder(this).setTitle("第三方数据许可").setItems(names) { _, i ->
            val scroll = ScrollView(this)
            scroll.addView(ui.text(assets.open("licenses/${names[i]}").bufferedReader().use { it.readText() }, 12f))
            AlertDialog.Builder(this).setTitle(names[i]).setView(scroll).setPositiveButton("关闭", null).show()
        }.setNegativeButton("关闭", null).show()
    }
    private fun refreshStatus() {
        val enabled = getSystemService(InputMethodManager::class.java).enabledInputMethodList.any { it.packageName == packageName }
        val selected = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.startsWith("$packageName/") == true
        status.text = when { selected -> "● 已启用并选中，随时可以输入"; enabled -> "● 已启用，请选择「放松输入法」"; else -> "○ 完成下面两步即可开始" }
    }
    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    override fun onResume() { super.onResume(); if (::status.isInitialized) refreshStatus() }
    override fun onDestroy() { store.close(); super.onDestroy() }
}
