package com.fanson.ime.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Vibrator
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.*
import com.fanson.ime.data.LocalStore
import com.fanson.ime.data.WubiFrequencyStore
import com.fanson.ime.engine.WubiEnterAction
import com.fanson.ime.engine.InputLanguage
import com.fanson.ime.R
import com.fanson.ime.keyboard.Palette
import com.fanson.ime.keyboard.HapticFeedback
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
        page.addView(ui.title(getString(R.string.app_name), 34f))
        page.addView(ui.text("全部离线 · 自己撑控", 16f, ui.palette.muted))
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
        page.addView(ui.text("试打正文不保存；开启五笔词频时会学习候选使用习惯。剪贴板查看和清空在键盘顶部的「剪贴板」面板中。", 13f, ui.palette.muted))
        section("输入习惯")
        val languagePreferences = LanguagePreferences(store)
        val defaultLanguageRow = ui.row()
        defaultLanguageRow.addView(ui.text("默认语言"), LinearLayout.LayoutParams(0, dp(48), 1f))
        val languages = InputLanguage.entries
        val defaultLanguageSpinner = Spinner(this).apply {
            contentDescription = "默认语言"
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, languages.map { it.label })
            setSelection(languages.indexOf(languagePreferences.defaultLanguage))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (languages[position] != languagePreferences.defaultLanguage) languagePreferences.setDefaultLanguage(languages[position])
                }
            }
        }
        defaultLanguageRow.addView(defaultLanguageSpinner); page.addView(defaultLanguageRow)
        page.addView(ui.text("初始为五笔86；修改后从所选语言开始，日常记住当前语言。地球短按往返英语与当前语言，长按选择其他语言。", 13f, ui.palette.muted))
        toggle("输入符号后返回键盘", "symbol_auto_return", true)
        page.addView(ui.text("在符号页输入一个符号后返回主键盘；关闭后可连续输入符号。表情输入不受影响。", 13f, ui.palette.muted))
        toggle("英语句首自动大写", "english_auto_capitalize", true)
        page.addView(ui.text("句首自动大写首字母；点按 Shift 可取消本次大写。密码、邮箱、网址和数字输入不自动大写，与自动拼写开关独立。", 13f, ui.palette.muted))
        toggle("四码唯一自动上屏", "auto_unique", true)
        toggle("第五码提交首选并开始新编码", "fifth_commit", true)
        toggle("五笔按本地词频排序", "wubi_frequency", true)
        page.addView(ui.text("仅五笔生效。按近期使用习惯调整同层候选，保留自定义短语和精确匹配优先；长期不用逐渐恢复固定顺序。关闭后停止学习并使用固定排序，已有学习保留。", 13f, ui.palette.muted))
        val enterRow = ui.row()
        enterRow.addView(ui.text("五笔回车键功能"), LinearLayout.LayoutParams(0, dp(48), 1f))
        val enterActions = WubiEnterAction.entries
        val enterSpinner = Spinner(this).apply {
            contentDescription = "五笔回车键功能"
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
        page.addView(ui.text("仅五笔86输入编码时生效；无编码时仍按输入框要求换行或执行发送等操作。首个候选无匹配时保留编码。", 13f, ui.palette.muted))
        hapticControls()
        toggle("字母键显示字根提示", "roots", false)
        page.addView(ui.text("开启后，五笔字母键显示简要字根；长按可查看该键完整字根。", 13f, ui.palette.muted))
        section("键盘外观")
        toggle("显示顶部数字行", "number_row", true)
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
        page.addView(ui.text("手动添加编码和短语，不自动从正文生成短语。自定义短语在相同编码下优先。", 13f, ui.palette.muted))
        val phraseButtons = ui.row()
        ui.addWeighted(phraseButtons, ui.button("添加短语") { addPhrase() })
        ui.addWeighted(phraseButtons, ui.button("管理短语") { managePhrases() })
        page.addView(phraseButtons)
        section("隐私与数据")
        page.addView(ui.text("安装包无网络权限；无账号、广告、统计或云词库，不保存整段输入历史。五笔默认在本机学习候选使用习惯，最多保存两万条衰减词频，收起键盘后批量保存；密码、禁止个性化学习的输入框和表情搜索不学习。另保存 emoji 使用次数与最近顺序，以及最近 10 项普通文本剪贴板历史。所有个人数据均排除系统备份。", 15f))
        page.addView(ui.text("输入法作为系统默认输入法且服务运行时记录后续复制内容，标记为敏感的内容不存入历史。剪贴板「一键清空」同时清除系统当前内容和本输入法历史，其他应用的历史需分别管理。", 13f, ui.palette.muted))
        clearButton("清空自定义短语", "删除全部手动短语及随之失效的学习记录；其他五笔词频、emoji 次数、设置和剪贴板保留。", "已清空自定义短语") { it.clearPhrases() }
        clearButton("清空 emoji 使用次数", "重置 emoji 使用次数与最近顺序；自定义短语、五笔词频、设置和剪贴板保留。", "已清空 emoji 次数") { it.clearEmojiUsage() }
        clearButton("清空五笔词频", "清除五笔学习记录，恢复初始候选顺序；自定义短语、emoji 次数、设置和剪贴板保留。开关保持原状态，开启时可重新学习。", "已清空五笔词频") { it.clearFrequency() }
        section("关于")
        val versionName = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        page.addView(ui.text("放松输入法 · FansonIME $versionName · Android 12+\n五笔码表：Rime wubi86 / LGPL-3.0\nEmoji：Unicode 16.0 / CLDR 46\n简繁：OpenCC 1.1.9 / Apache-2.0\n英文词表：FrequencyWords 2018 / CC BY-SA 4.0\n读音：pinyin-data / phrase-pinyin-data · MIT", 13f, ui.palette.muted))
        page.addView(ui.text("作者邮箱：cnxfs@foxmail.com", 15f).apply { setTextIsSelectable(true) })
        page.addView(ui.button("第三方数据与许可") { showLicenses() }, LinearLayout.LayoutParams(-1, dp(48)))
        refreshStatus()
    }

    private fun section(label: String) { page.addView(ui.title(label, 19f).apply { setPadding(dp(8), dp(24), dp(8), dp(8)) }) }
    private fun clearButton(label: String, explanation: String, success: String, action: (WubiFrequencyStore) -> Unit) {
        page.addView(ui.button(label) {
            AlertDialog.Builder(this).setTitle("$label？").setMessage(explanation)
                .setNegativeButton("取消", null).setPositiveButton("清空") { _, _ -> dataAction(success, action) }.show()
        }, LinearLayout.LayoutParams(-1, dp(48)))
    }
    private fun dataAction(success: String, action: (WubiFrequencyStore) -> Unit) {
        WubiFrequencyStore.edit(this, action) { saved ->
            runOnUiThread { if (!isFinishing && !isDestroyed) toast(if (saved) success else "操作未完成，请重试") }
        }
    }
    private fun toggle(label: String, key: String, default: Boolean): Switch {
        val toggle = Switch(this).apply {
            text = label; textSize = 15f; setTextColor(ui.palette.ink); minHeight = dp(52)
            isChecked = store.get(key, default.toString()).toBooleanStrictOrNull() ?: default
            setOnCheckedChangeListener { _, checked -> store.put(key, checked.toString()) }
        }
        page.addView(toggle)
        return toggle
    }
    private fun hapticControls() {
        val enabled = toggle("按键轻震动", "haptic", false)
        val label = ui.text("")
        val slider = SeekBar(this).apply {
            contentDescription = "振动强度"
            min = 0; max = 100
            progress = HapticFeedback.strength(store.get("haptic_strength", HapticFeedback.DEFAULT_STRENGTH.toString()))
            isEnabled = enabled.isChecked
        }
        fun updateLabel(level: Int) {
            label.text = getString(if (level == 0) R.string.haptic_strength_muted else R.string.haptic_strength_value, level)
            slider.stateDescription = getString(R.string.haptic_strength_percent, level)
        }
        fun saveAndPreview(level: Int) {
            store.put("haptic_strength", level.toString())
            if (enabled.isChecked) HapticFeedback.preview(getSystemService(Vibrator::class.java), level)
        }
        updateLabel(slider.progress)
        var dragging = false
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar) { dragging = true }
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                dragging = false
                saveAndPreview(seekBar.progress)
            }
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                updateLabel(progress)
                // Accessibility/D-pad adjustments have no touch stop callback.
                if (fromUser && !dragging) saveAndPreview(progress)
            }
        })
        enabled.setOnCheckedChangeListener { _, checked ->
            store.put("haptic", checked.toString())
            slider.isEnabled = checked
        }
        page.addView(label); page.addView(slider)
        page.addView(ui.text("开启轻震动后可调整；松手体验效果，0% 不振动。不同手机的振感可能不同。", 13f, ui.palette.muted))
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
                .setPositiveButton("删除") { _, _ -> dataAction("已删除") { it.removePhrase(phrase.code, phrase.text) } }.show()
        }.setNegativeButton("关闭", null).show()
    }
    private fun showLicenses() {
        val names = arrayOf("RIME-AUTHORS.txt", "RIME-LGPL-3.0.txt", "GPL-3.0.txt", "UNICODE-LICENSE.txt", "COMPLETION-NOTICE.txt", "OPENCC-AUTHORS.txt", "OPENCC-LICENSE.txt", "CC-BY-SA-4.0.txt", "FREQUENCYWORDS-README.md", "WUBI-ROOTS-NOTICE.txt", "ARPHICPL.TXT", "FONT-COPYRIGHT.txt", "UPSTREAM-FONT-CHANGES.adoc", "PRONUNCIATION-NOTICE.txt", "pinyin-data-MIT.txt", "phrase-pinyin-data-MIT.txt")
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
