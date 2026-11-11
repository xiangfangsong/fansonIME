package com.fanson.ime

import android.app.Activity
import android.app.Instrumentation
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.os.PersistableBundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.fanson.ime.data.LocalStore
import com.fanson.ime.data.WubiRepository
import com.fanson.ime.data.PronunciationRepository
import com.fanson.ime.emoji.EmojiRepository
import com.fanson.ime.engine.InputLanguage
import com.fanson.ime.engine.Candidate
import com.fanson.ime.engine.TraditionalConverter
import com.fanson.ime.engine.EnglishLexicon
import com.fanson.ime.engine.WubiEngine
import com.fanson.ime.engine.WubiEnterAction
import com.fanson.ime.keyboard.CandidateStrip
import com.fanson.ime.keyboard.EmojiCategoryStrip
import com.fanson.ime.keyboard.Palette
import com.fanson.ime.keyboard.Ui
import com.fanson.ime.keyboard.SymbolCatalog
import com.fanson.ime.keyboard.SymbolPanel
import com.fanson.ime.keyboard.dp
import com.fanson.ime.settings.MainActivity
import com.fanson.ime.settings.LanguagePreferences
import java.io.File

/** Run only on a disposable emulator. No existing user clip is read or retained. */
class SmokeInstrumentation : Instrumentation() {
    private val passed = mutableListOf<String>()
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        try {
            val store = LocalStore(targetContext)
            store.put("input_language", "wubi86")
            store.put("default_language", "wubi86")
            store.put("last_non_english_language", "")
            store.put("wubi_enter_action", "commit_code")
            store.put("number_row", "true")
            store.put("roots", "false")
            store.put("haptic", "false")
            store.put("theme", "system")
            passed += WubiFrequencyChecks.run(this)
            // Simulate an upgraded installation with no preference for this new setting.
            android.database.sqlite.SQLiteDatabase.openDatabase(
                File(targetContext.noBackupFilesDir, "personal.db").absolutePath,
                null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
            ).use {
                it.delete("preferences", "key=?", arrayOf("symbol_auto_return"))
                it.delete("preferences", "key=?", arrayOf("wubi_frequency"))
                it.delete("preferences", "key=?", arrayOf("haptic_strength"))
            }
            check(store.get("symbol_auto_return", "missing") == "missing")
            WubiRepository(targetContext, store).use { repository ->
                check(repository.lookup("a").first().text == "工")
                check(repository.lookup("r").first().text == "的")
                check(repository.lookup("wqiy").any { it.text == "你" })
                passed += "offline_dictionary"
                store.addPhrase("aaaa", "测试短语")
                check(repository.lookup("aaaa").first().text == "测试短语")
                store.removePhrase("aaaa", "测试短语")
                passed += "custom_phrase"
                val converter = targetContext.assets.open("traditional.tsv").bufferedReader().use { TraditionalConverter.read(it.lineSequence()) }
                check(converter.convert("发").containsAll(listOf("發", "髮")))
                check(converter.convert("头发发展") == listOf("頭髮發展"))
                val traditionalEngine = WubiEngine(repository, converter).apply { traditionalMode = true }
                "ntcy".forEach { check(traditionalEngine.letter(it).commits.isEmpty()) }
                check(traditionalEngine.candidates.map { it.text }.containsAll(listOf("發", "髮")))
                passed += "offline_traditional_data_and_ambiguous_four_code"
                val vocabulary = targetContext.assets.open("english.txt").bufferedReader().use { EnglishLexicon(it.readLines()) }
                check(vocabulary.lookup("app").any { it.text == "apple" })
                check(vocabulary.lookup("APP").any { it.text == "APPLE" })
                passed += "offline_english_ranked_vocabulary"
            }
            val emojis = EmojiRepository(targetContext)
            val xingReading: String
            PronunciationRepository(targetContext).use { readings ->
                xingReading = readings.reading("行")
                check(xingReading.split(" / ").containsAll(listOf("xíng", "háng")))
                check(readings.reading("重").split(" / ").containsAll(listOf("zhòng", "chóng")))
                check(readings.reading("银行") == "yín háng")
                check(readings.reading("銀行") == "yín háng")
                check(readings.reading("重慶") == "chóng qìng")
                check(readings.reading("𠀀") == "hē")
                check(readings.reading("𠮷") == "暂无读音")
            }
            passed += "offline_polyphones_phrase_traditional_supplementary_and_missing_readings"
            check(emojis.all.size > 3000)
            check(emojis.filter("popular", "笑", emptyMap()).isNotEmpty())
            check(emojis.all.any { it.text == "👨‍👩‍👧‍👦" })
            check("收藏" !in emojis.categories)
            check(emojis.filter("People & Body", "", emptyMap()).any { it.text == "👍🏽" })
            passed += "offline_emoji_data"
            store.clearEmojiUsage()
            store.recordEmojiUsage("😀"); store.recordEmojiUsage("😀"); store.recordEmojiUsage("😂")
            check(store.emojiUsage()["😀"]?.count == 2L && store.emojiUsage()["😂"]?.count == 1L)
            LocalStore(targetContext).use { check(it.emojiUsage()["😀"]?.count == 2L) }
            store.recordEmojiUsage("😂")
            check(emojis.filter("popular", "", store.emojiUsage()).first().text == "😂")
            store.clearEmojiUsage()
            check(store.emojiUsage().isEmpty())
            passed += "emoji_usage_count_persist_recency_clear"
            store.clearClipboardHistory()
            (1..12).forEach { store.recordClipboard("历史容量测试$it") }
            check(store.clipboardHistory().map { it.text } == (12 downTo 3).map { "历史容量测试$it" })
            passed += "clipboard_history_capacity_order"
            store.recordClipboard("历史容量测试5")
            check(store.clipboardHistory().first().text == "历史容量测试5")
            check(store.clipboardHistory().size == 10)
            store.recordClipboard("敏感不记录", sensitive = true)
            store.recordClipboard("   ")
            check(store.clipboardHistory().none { it.text == "敏感不记录" || it.text.isBlank() })
            passed += "clipboard_history_dedup_sensitive"
            store.deleteClipboardEntry(store.clipboardHistory().first().id)
            check(store.clipboardHistory().none { it.text == "历史容量测试5" })
            LocalStore(targetContext).use { reopened -> check(reopened.clipboardHistory().size == 9) }
            passed += "clipboard_history_delete_persist"
            store.clearClipboardHistory()
            val languagePreferences = LanguagePreferences(store)
            store.put("default_language", "unknown")
            store.put("last_non_english_language", "english")
            check(languagePreferences.defaultLanguage == InputLanguage.WUBI)
            check(languagePreferences.quickSwitchTarget(InputLanguage.ENGLISH) == InputLanguage.WUBI)
            store.put("default_language", "korean")
            store.put("input_language", "english")
            store.put("last_non_english_language", "")
            check(languagePreferences.preferredLanguage == InputLanguage.ENGLISH)
            check(languagePreferences.quickSwitchTarget(InputLanguage.ENGLISH) == InputLanguage.KOREAN)
            store.put("last_non_english_language", "unknown")
            check(languagePreferences.quickSwitchTarget(InputLanguage.ENGLISH) == InputLanguage.KOREAN)
            languagePreferences.recordSelection(InputLanguage.KOREAN, InputLanguage.ENGLISH)
            LocalStore(targetContext).use { reopened ->
                val restored = LanguagePreferences(reopened)
                check(restored.preferredLanguage == InputLanguage.ENGLISH)
                check(restored.quickSwitchTarget(InputLanguage.ENGLISH) == InputLanguage.KOREAN)
            }
            languagePreferences.setDefaultLanguage(InputLanguage.ENGLISH)
            check(languagePreferences.quickSwitchTarget(InputLanguage.ENGLISH) == InputLanguage.WUBI)
            languagePreferences.setDefaultLanguage(InputLanguage.WUBI)
            store.put("last_non_english_language", "")
            passed += "language_preferences_legacy_english_default_fallback_invalid_values_and_persistence"
            store.close()
            val activity = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitForIdleSync()
            check(targetContext.applicationInfo.loadLabel(targetContext.packageManager).toString() == "放松输入法")
            passed += "application_name"
            val installedVersion = targetContext.packageManager.getPackageInfo(targetContext.packageName, 0).versionName
            runOnMainSync {
                check(allViews(activity.window.decorView).filterIsInstance<TextView>().any {
                    it.text.toString().startsWith("放松输入法 · FansonIME $installedVersion · Android 12+")
                })
            }
            passed += "application_about_version_matches_installed_package"
            checkHapticSettings(activity)
            runOnMainSync {
                val ui = Ui(targetContext, Palette.of(targetContext, "light"))
                var selected: Candidate? = null
                var requestedExpand = false
                val strip = CandidateStrip(ui, { selected = it }, { requestedExpand = true }, {}, {})
                fun measure(width: Int) {
                    strip.measure(View.MeasureSpec.makeMeasureSpec(targetContext.dp(width), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(targetContext.dp(44), View.MeasureSpec.EXACTLY))
                    strip.layout(0, 0, strip.measuredWidth, strip.measuredHeight)
                }
                strip.submit(emptyList(), false, ""); measure(320); check(!strip.hasOverflow)
                strip.submit(listOf(Candidate("甲", "a")), false, ""); measure(320)
                check(!strip.hasOverflow && strip.visibleCandidateCount == 1)
                (strip.getChildAt(0) as Button).performClick(); check(selected?.text == "甲")
                strip.submit(listOf(Candidate("甲", "a"), Candidate("乙", "a")), false, ""); measure(320)
                check(!strip.hasOverflow && strip.visibleCandidateCount == 2)
                selected = null
                strip.submit(List(30) { Candidate("候选$it", "a") }, false, ""); measure(320)
                check(strip.hasOverflow && strip.visibleCandidateCount in 1..29)
                (strip.getChildAt(0) as Button).performClick(); check(!requestedExpand && selected?.text == "候选0")
                val expand = (0 until strip.childCount).map { strip.getChildAt(it) }.filterIsInstance<Button>().first { it.contentDescription == "展开候选" }
                check(expand.text.isEmpty() && expand.foreground != null && expand.visibility == View.VISIBLE)
                expand.performClick(); check(requestedExpand)
                strip.submit(listOf(Candidate("长".repeat(40), "a")), false, ""); measure(200)
                check(strip.hasOverflow)
                measure(2000); check(!strip.hasOverflow)
                measure(200); check(strip.hasOverflow)
                strip.submit(listOf(Candidate("很长的候选词".repeat(4), "aaaa"), Candidate("乙", "aaaa")), false, "", "编码：aaaa")
                for (width in listOf(0, 20, 120, 320, 2000)) {
                    measure(width)
                    check(strip.composingLabel.right <= strip.width * .3f + 1)
                    (0 until strip.childCount).map { strip.getChildAt(it) }.filterIsInstance<Button>().filter { it.visibility == View.VISIBLE }.forEach { button ->
                        check(button.left >= strip.composingLabel.right && button.right <= strip.width)
                    }
                }
                strip.submit(emptyList(), false, "无匹配", "编码：abcd"); measure(120)
                check(strip.showHint("请退格修正") && strip.composingLabel.text.toString() == "编码：abcd")
            }
            passed += "candidate_row_fit_overflow_resize"
            passed += "candidate_prefix_bounds_and_hints_keep_composition"
            runOnMainSync {
                var selected: Candidate? = null
                var requestedExpand = false
                val strip = CandidateStrip(Ui(targetContext, Palette.of(targetContext, "light")), { selected = it }, { requestedExpand = true }, {}, {})
                val candidates = listOf(Candidate("app", ""), Candidate("apple", "")) + List(20) { Candidate("application$it", "") }
                strip.submit(candidates, false, "")
                strip.measure(View.MeasureSpec.makeMeasureSpec(targetContext.dp(320), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(targetContext.dp(44), View.MeasureSpec.EXACTLY))
                strip.layout(0, 0, strip.measuredWidth, strip.measuredHeight)
                check(strip.hasOverflow && strip.visibleCandidateCount >= 2)
                (strip.getChildAt(1) as Button).performClick()
                check(selected?.text == "apple" && !requestedExpand)
                val arrow = (0 until strip.childCount).map { strip.getChildAt(it) }.filterIsInstance<Button>().first { it.contentDescription == "展开候选" }
                check(arrow.visibility == View.VISIBLE)
                arrow.performClick(); check(requestedExpand)
            }
            passed += "english_overflow_row_apple_selects_arrow_expands"
            runOnMainSync {
                val strip = CandidateStrip(Ui(targetContext, Palette.of(targetContext, "light")), {}, {}, {}, {})
                val reading = "xíng / háng / héng / xìng / hàng"
                strip.submit(listOf(Candidate("行", "tf", true, reading), Candidate("工", "a", true, "gōng")), false, "", "编码：tf", true)
                fun measure(width: Int) {
                    strip.measure(View.MeasureSpec.makeMeasureSpec(targetContext.dp(width), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(targetContext.dp(44), View.MeasureSpec.EXACTLY))
                    strip.layout(0, 0, strip.measuredWidth, strip.measuredHeight)
                }
                for (width in listOf(0, 20, 120, 320, 2000)) {
                    measure(width)
                    val scroll = allViews(strip).filterIsInstance<android.widget.HorizontalScrollView>().single()
                    check(scroll.left >= strip.composingLabel.right && scroll.right <= strip.width)
                    val button = allViews(scroll).filterIsInstance<Button>().first()
                    check(button.text.toString() == "行\n$reading" && button.ellipsize == null)
                    check(button.layout.lineCount == 2)
                    check(button.layout.getEllipsisCount(1) == 0)
                    check(button.layout.getLineWidth(1) <= button.width - button.paddingLeft - button.paddingRight + 1)
                    check(strip.height == targetContext.dp(44))
                }
                measure(120); check(strip.hasOverflow)
                measure(2000); check(!strip.hasOverflow)
            }
            passed += "reading_row_fixed_height_full_polyphones_scroll_and_resize_bounds"
            val inputMethod = targetContext.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                .enabledInputMethodList.first { it.packageName == targetContext.packageName }
            check(inputMethod.loadLabel(targetContext.packageManager).toString() == "放松输入法")
            check(inputMethod.subtypeCount == 0)
            passed += "input_method_name_without_subtype"
            val editor = allViews(activity.window.decorView).filterIsInstance<EditText>().first()
            runOnMainSync {
                check(editor.gravity == android.view.Gravity.TOP or android.view.Gravity.START)
                check(editor.inputType and android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0)
            }
            passed += "local_trial_multiline_top_start_alignment"
            val enterSpinner = allViews(activity.window.decorView).filterIsInstance<android.widget.Spinner>().first { it.contentDescription == "五笔回车键功能" }
            runOnMainSync {
                check(allViews(activity.window.decorView).filterIsInstance<android.widget.Switch>().first { it.text.toString() == "输入符号后返回键盘" }.isChecked)
            }
            passed += "symbol_auto_return_setting_initially_enabled"
            runOnMainSync { check((0 until enterSpinner.count).map { enterSpinner.getItemAtPosition(it).toString() } == listOf("上屏五笔编码", "清除五笔编码", "上屏首个候选")) }
            screenshot("18-local-trial-settings")
            screenshot("41-symbol-auto-return-setting-0.3.3")
            screenshot("39-default-language-0.3.2")
            runOnMainSync { editor.requestFocus() }
            await {
                var focused = false
                runOnMainSync { focused = editor.hasWindowFocus() && editor.hasFocus() }
                focused
            }
            // Starting instrumentation force-stops this same package and can
            // leave IMMS with an old, unbound service connection. Re-select it
            // after the instrumented activity is attached (emulator tests only).
            val manager = targetContext.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
            manager.enabledInputMethodList.firstOrNull { it.packageName != targetContext.packageName }?.let {
                shell("ime set ${it.id}")
            }
            shell("ime set ${targetContext.packageName}/.ime.FansonInputMethodService")
            // Exercise the same explicit touch path as the user. An implicit
            // showSoftInput can be suppressed with the emulator's hardware keyboard.
            var editorX = 0f
            var editorY = 0f
            runOnMainSync {
                val rect = android.graphics.Rect()
                check(editor.getGlobalVisibleRect(rect))
                editorX = rect.exactCenterX(); editorY = rect.exactCenterY()
            }
            val touchTime = SystemClock.uptimeMillis()
            sendPointerSync(android.view.MotionEvent.obtain(touchTime, touchTime, android.view.MotionEvent.ACTION_DOWN, editorX, editorY, 0))
            sendPointerSync(android.view.MotionEvent.obtain(touchTime, touchTime + 30, android.view.MotionEvent.ACTION_UP, editorX, editorY, 0))
            await { targetContext.getSystemService(android.view.inputmethod.InputMethodManager::class.java).isActive(editor) }
            runOnMainSync { targetContext.getSystemService(android.view.inputmethod.InputMethodManager::class.java).showSoftInput(editor, android.view.inputmethod.InputMethodManager.SHOW_FORCED) }
            await { findButton("剪贴板") != null }
            runOnMainSync {
                listOf("剪贴板", "符号", "表情", "设置", "收起键盘", "换行", "切换大小写").forEach { label ->
                    val icon = windows().filterIsInstance<Button>().first { visible(it) && it.contentDescription?.toString() == label }
                    check(icon.text.isEmpty() && icon.foreground != null) { "Expected a vector icon for $label" }
                }
                check(windows().filterIsInstance<Button>().none { visible(it) && it.text.toString() == "切换输入法" })
                val settings = windows().filterIsInstance<Button>().first { visible(it) && it.contentDescription?.toString() == "设置" }
                val hide = windows().filterIsInstance<Button>().first { visible(it) && it.contentDescription?.toString() == "收起键盘" }
                check(settings.parent === hide.parent && settings.x < hide.x)
            }
            passed += "vector_toolbar_enter_hide_layout"
            await { findButton("切换语言")?.isEnabled == true }
            fun refreshKeyboard() {
                runOnMainSync { manager.hideSoftInputFromWindow(editor.windowToken, 0) }
                await { findButton("退格") == null }
                runOnMainSync { manager.restartInput(editor); manager.showSoftInput(editor, android.view.inputmethod.InputMethodManager.SHOW_FORCED) }
                await { findButton("剪贴板") != null && findButton("切换语言")?.isEnabled == true }
            }
            fun setToggle(label: String, checked: Boolean) {
                runOnMainSync { allViews(activity.window.decorView).filterIsInstance<android.widget.Switch>().first { it.text.toString() == label }.isChecked = checked }
                waitForIdleSync()
                refreshKeyboard()
            }
            fun checkNumberRow(shown: Boolean) {
                runOnMainSync {
                    val rows = windows().filterIsInstance<android.widget.LinearLayout>().filter { visible(it) && it.contentDescription == "数字行" }
                    check(rows.size == if (shown) 1 else 0)
                    if (shown) check((0 until rows.single().childCount).map { (rows.single().getChildAt(it) as Button).text.toString() } == "1234567890".map { it.toString() })
                }
            }
            checkFrequencyUi(activity, editor, ::refreshKeyboard)
            longPress("切换语言")
            runOnMainSync {
                val menu = windows().filterIsInstance<android.widget.LinearLayout>().first { visible(it) && it.contentDescription == "输入语言列表" }
                val items = (0 until menu.childCount).map { menu.getChildAt(it) as Button }
                check(items.map { it.text.toString() } == listOf("English", "五笔86", "한국어"))
                check(items.map { it.isSelected } == listOf(false, true, false))
            }
            screenshot("31-language-selector")
            screenshot("40-language-menu-0.3.2")
            click("五笔86")
            chooseLanguage(InputLanguage.KOREAN)
            checkCandidateArea(false); checkNumberRow(true)
            runOnMainSync {
                "qwertyuiopasdfghjklzxcvbnm".forEach { key ->
                    val button = findKeyboardButton(key)
                    check(button.text.toString() == com.fanson.ime.engine.KoreanEngine.keyLabel(key).toString())
                    check(!button.isLongClickable)
                }
                check(!findButtonOnMain("한/漢（暂未开放）").isEnabled)
                check(findButtonOnMain("␣ 한국어").contentDescription == "空格")
                check(windows().filterIsInstance<Button>().none { visible(it) && it.contentDescription == "Z / 查读音" })
            }
            passed += "fixed_language_menu_order_current_check_globe_korean_pc_layout_and_placeholder"
            fun koreanKeys(keys: String, burst: Boolean = false) {
                if (burst) runOnMainSync { keys.forEach { findKeyboardButton(it).performClick() } }
                else keys.forEach { key ->
                    if (key.isUpperCase()) click("韩语上档")
                    runOnMainSync { findKeyboardButton(key.lowercaseChar()).performClick() }
                    waitForIdleSync()
                }
            }
            koreanKeys("g"); await { editor.text.toString() == "ㅎ" }
            koreanKeys("k"); await { editor.text.toString() == "하" }
            koreanKeys("s"); await { editor.text.toString() == "한" }
            checkCandidateArea(false)
            runOnMainSync { check(android.view.inputmethod.BaseInputConnection.getComposingSpanStart(editor.text) >= 0) }
            screenshot("32-korean-composition")
            for (expected in listOf("하", "ㅎ", "")) { click("退格"); await { editor.text.toString() == expected } }
            runOnMainSync { check(android.view.inputmethod.BaseInputConnection.getComposingSpanStart(editor.text) < 0) }
            passed += "korean_live_composition_jamo_backspace_and_toolbar_without_candidates"
            koreanKeys("gksrmf"); await { editor.text.toString() == "한글" }
            click("空格"); await { editor.text.toString() == "한글 " }
            runOnMainSync { editor.setText("") }; refreshKeyboard()
            koreanKeys("rksk"); await { editor.text.toString() == "가나" }
            click("空格"); koreanKeys("ekfrk"); await { editor.text.toString() == "가나 달가" }
            click("空格"); koreanKeys("rkqtk"); await { editor.text.toString() == "가나 달가 갑사" }
            passed += "korean_words_simple_and_compound_final_resyllabification_ui"
            click("空格"); koreanKeys("Rk"); await { editor.text.toString().endsWith(" 까") }
            runOnMainSync { check(findKeyboardButton('r').text.toString() == "ㄱ" && !findButtonOnMain("韩语上档").isSelected) }
            click("空格"); koreanKeys("rhk"); await { editor.text.toString().endsWith(" 까 과") }
            passed += "korean_shift_one_shot_and_compound_vowels_ui"
            runOnMainSync { editor.setText("") }; refreshKeyboard()
            koreanKeys("dkssudgktpdy", burst = true)
            await { editor.text.toString() == "안녕하세요" }
            screenshot("33-korean-word")
            passed += "korean_fast_typing_preserves_full_word_and_composing_cursor_callbacks"
            for (action in WubiEnterAction.entries) {
                runOnMainSync { editor.setText(""); enterSpinner.setSelection(WubiEnterAction.entries.indexOf(action)) }
                await { LocalStore(targetContext).use { it.get("wubi_enter_action", "") == action.value } }
                refreshKeyboard()
                koreanKeys("gks"); click("换行")
                await { editor.text.toString() == "한\n" }
                runOnMainSync { check(android.view.inputmethod.BaseInputConnection.getComposingSpanStart(editor.text) < 0) }
            }
            passed += "korean_return_confirms_and_newlines_once_independent_of_all_wubi_return_settings"
            val koreanInputType = editor.inputType
            val koreanImeOptions = editor.imeOptions
            var sent = 0
            runOnMainSync {
                editor.setText(""); editor.setSingleLine(true)
                editor.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEND
                editor.setOnEditorActionListener { _, action, _ -> if (action == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) { sent++; true } else false }
                manager.restartInput(editor)
            }
            await { findButton("发送") != null && findButton("␣ 한국어") != null }
            koreanKeys("gks"); click("发送"); await { sent == 1 && editor.text.toString() == "한" }
            runOnMainSync {
                check(android.view.inputmethod.BaseInputConnection.getComposingSpanStart(editor.text) < 0)
                editor.setOnEditorActionListener(null)
                editor.setSingleLine(false); editor.inputType = koreanInputType; editor.imeOptions = koreanImeOptions
                editor.setText(""); manager.restartInput(editor)
            }
            await { findButton("换行") != null && findButton("␣ 한국어") != null }
            passed += "korean_send_confirms_then_performs_editor_action_with_one_press"
            koreanKeys("gks"); chooseLanguage(InputLanguage.KOREAN)
            koreanKeys("k"); await { editor.text.toString() == "하나" }
            chooseLanguage(InputLanguage.ENGLISH); await { editor.text.toString() == "하나" }
            "app".forEach { click(it.toString()) }
            chooseLanguage(InputLanguage.KOREAN); await { editor.text.toString() == "하나app" }
            koreanKeys("gks"); chooseLanguage(InputLanguage.WUBI); await { editor.text.toString() == "하나app한" }
            passed += "same_language_menu_keeps_composition_and_cross_language_switch_confirms_once"
            runOnMainSync { editor.setText("") }
            click("a"); await { findText("编码：a") != null }
            longPress("切换语言"); runOnMainSync { check(editor.text.isEmpty()) }
            screenshot("34-selector-during-wubi-composition")
            click("English"); await { findButton("␣ English") != null && editor.text.toString() == "工" }
            chooseLanguage(InputLanguage.WUBI)
            val switchingNoMatch = LocalStore(targetContext).use { personal -> WubiRepository(targetContext, personal).use { repo -> listOf("yyyy", "abcd", "qwer", "yxyx", "xxyy").first { repo.lookup(it).isEmpty() } } }
            switchingNoMatch.forEach { click(it.toString()) }
            longPress("切换语言"); click("한국어")
            await { findText("编码：$switchingNoMatch") != null }
            check(findButton("␣ 五笔86") != null)
            repeat(switchingNoMatch.length) { click("退格") }
            passed += "globe_stays_available_during_candidates_and_unmatched_language_switch_preserves_code"
            chooseLanguage(InputLanguage.KOREAN)
            runOnMainSync { editor.setText("") }; refreshKeyboard()
            koreanKeys("gks"); runOnMainSync { editor.setSelection(0) }
            await { android.view.inputmethod.BaseInputConnection.getComposingSpanStart(editor.text) < 0 }
            koreanKeys("r"); await { editor.text.toString() == "ㄱ한" }
            click("123"); click("1"); click("返回")
            await { findButton("␣ 한국어") != null && editor.text.toString() == "ㄱ1한" }
            passed += "korean_cursor_move_ends_composition_and_numeric_panel_preserves_language"
            runOnMainSync { editor.setText("") }; refreshKeyboard()
            koreanKeys("gks"); longPress("切换语言")
            uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            await {
                var closed = false
                runOnMainSync { closed = windows().none { visible(it) && it.contentDescription == "输入语言列表" } }
                closed && findButton("␣ 한국어") != null
            }
            koreanKeys("k"); await { editor.text.toString() == "하나" }
            runOnMainSync { editor.setSelection(0, editor.length()) }
            await { android.view.inputmethod.BaseInputConnection.getComposingSpanStart(editor.text) < 0 }
            koreanKeys("gks"); await { editor.text.toString() == "한" }
            runOnMainSync { targetContext.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("korean-test", "붙여넣기")) }
            click("剪贴板"); await { findButton("粘贴到输入框")?.isEnabled == true }
            click("粘贴到输入框"); await { editor.text.toString() == "한붙여넣기" }
            click("‹ 返回键盘"); await { findButton("␣ 한국어") != null }
            click("符号"); await { findButton("返回键盘") != null }
            click("+")
            await { findButton("␣ 한국어") != null && editor.text.toString() == "한붙여넣기+" }
            passed += "korean_popup_back_cancel_selection_replace_clipboard_paste_and_symbol_return"
            for (mode in listOf(InputLanguage.KOREAN, InputLanguage.ENGLISH)) {
                chooseLanguage(mode)
                click("表情"); await { findButton("😀") != null }
                check(findButton("五笔搜索 emoji") == null)
                click("😀"); click("‹ 键盘")
                await { findButton("␣ ${mode.label}") != null }
            }
            passed += "english_and_korean_emoji_browsing_hides_search_and_keeps_language"
            chooseLanguage(InputLanguage.KOREAN)
            runOnMainSync { editor.setText("") }; refreshKeyboard()
            check(findButton("␣ 한국어") != null)
            runOnMainSync { editor.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD; manager.restartInput(editor) }
            await { findButton("␣ English") != null }
            LocalStore(targetContext).use { check(it.get("input_language", "") == "korean") }
            runOnMainSync { editor.inputType = koreanInputType; manager.restartInput(editor) }
            await { findButton("␣ 한국어") != null }
            passed += "remembered_language_survives_reopen_and_temporary_password_english_override"
            chooseLanguage(InputLanguage.WUBI)
            runOnMainSync { editor.setText("") }; refreshKeyboard()
            chooseLanguage(InputLanguage.ENGLISH)
            LocalStore(targetContext).use { it.put("last_non_english_language", "") }
            refreshKeyboard(); await { findButton("␣ English") != null }
            click("切换语言"); await { findButton("␣ 五笔86") != null }
            passed += "english_start_without_return_history_uses_initial_wubi_default"
            click("a"); await { findText("编码：a") != null }
            tap("切换语言")
            await { findButton("␣ English") != null && editor.text.toString() == "工" }
            runOnMainSync { check(windows().none { visible(it) && it.contentDescription == "输入语言列表" }) }
            tap("切换语言"); await { findButton("␣ 五笔86") != null }
            check(editor.text.toString() == "工")
            passed += "globe_short_tap_wubi_english_round_trip_confirms_candidate_without_menu"
            switchingNoMatch.forEach { click(it.toString()) }
            click("切换语言")
            await { findText("编码：$switchingNoMatch") != null }
            LocalStore(targetContext).use {
                check(it.get("input_language", "") == "wubi86")
                check(it.get("last_non_english_language", "") == "wubi86")
            }
            check(findButton("␣ 五笔86") != null && editor.text.toString() == "工")
            repeat(switchingNoMatch.length) { click("退格") }
            passed += "globe_short_tap_unmatched_wubi_preserves_code_language_and_return_target"
            chooseLanguage(InputLanguage.KOREAN)
            runOnMainSync { editor.setText("") }; refreshKeyboard()
            koreanKeys("gks"); tap("切换语言")
            await { findButton("␣ English") != null && editor.text.toString() == "한" }
            "app".forEach { click(it.toString()) }; tap("切换语言")
            await { findButton("␣ 한국어") != null && editor.text.toString() == "한app" }
            runOnMainSync { check(android.view.inputmethod.BaseInputConnection.getComposingSpanStart(editor.text) < 0) }
            repeat(2) { click("切换语言"); await { findButton("␣ English") != null }; click("切换语言"); await { findButton("␣ 한국어") != null } }
            check(editor.text.toString() == "한app")
            koreanKeys("gks"); longPress("切换语言")
            runOnMainSync {
                check(findButtonOnMain("␣ 한국어").isShown)
                check(windows().any { visible(it) && it.contentDescription == "输入语言列表" })
                check(android.view.inputmethod.BaseInputConnection.getComposingSpanStart(editor.text) >= 0)
            }
            click("한국어"); koreanKeys("k")
            await { editor.text.toString() == "한app하나" }
            passed += "globe_korean_english_round_trip_composing_and_real_long_press_suppresses_short_tap"
            chooseLanguage(InputLanguage.WUBI)
            runOnMainSync { editor.setText("") }; refreshKeyboard()
            val defaultLanguageSpinner = allViews(activity.window.decorView).filterIsInstance<android.widget.Spinner>().first { it.contentDescription == "默认语言" }
            runOnMainSync {
                check((0 until defaultLanguageSpinner.count).map { defaultLanguageSpinner.getItemAtPosition(it).toString() } == listOf("English", "五笔86", "한국어"))
                check(defaultLanguageSpinner.selectedItemPosition == InputLanguage.entries.indexOf(InputLanguage.WUBI))
                defaultLanguageSpinner.setSelection(InputLanguage.entries.indexOf(InputLanguage.KOREAN))
            }
            await { LocalStore(targetContext).use { it.get("default_language", "") == "korean" && it.get("input_language", "") == "korean" } }
            refreshKeyboard(); await { findButton("␣ 한국어") != null }
            chooseLanguage(InputLanguage.ENGLISH)
            LocalStore(targetContext).use { it.put("last_non_english_language", "") }
            refreshKeyboard(); await { findButton("␣ English") != null }
            click("切换语言"); await { findButton("␣ 한국어") != null }
            runOnMainSync { defaultLanguageSpinner.setSelection(InputLanguage.entries.indexOf(InputLanguage.ENGLISH)) }
            await { LocalStore(targetContext).use { it.get("default_language", "") == "english" && it.get("input_language", "") == "english" } }
            refreshKeyboard(); await { findButton("␣ English") != null }
            click("切换语言"); await { findButton("␣ 五笔86") != null }
            runOnMainSync { defaultLanguageSpinner.setSelection(InputLanguage.entries.indexOf(InputLanguage.WUBI)) }
            await { LocalStore(targetContext).use { it.get("default_language", "") == "wubi86" && it.get("input_language", "") == "wubi86" } }
            refreshKeyboard(); await { findButton("␣ 五笔86") != null }
            passed += "default_language_setting_all_options_persist_apply_and_english_start_fallback"
            click("切换大小写"); await { findButton("␣ English") != null }
            click("切换语言"); await { findButton("␣ 五笔86") != null }
            passed += "wubi_shift_to_uppercase_english_retains_wubi_quick_return"
            chooseLanguage(InputLanguage.WUBI)
            LocalStore(targetContext).use { it.clearEmojiUsage() }
            runOnMainSync { editor.setText(""); enterSpinner.setSelection(0) }; refreshKeyboard()
            checkCandidateArea(false)
            var idleKeyboardHeight = 0
            runOnMainSync { idleKeyboardHeight = ((windows().filterIsInstance<CandidateStrip>().first { it.isAttachedToWindow && (it.parent as View).isShown }.parent as View).parent as View).height }
            screenshot("25-idle-toolbar")
            click("a"); await { findText("编码：a") != null }
            checkCandidateArea(true)
            runOnMainSync { check(((windows().filterIsInstance<CandidateStrip>().first { visible(it) }.parent as View).parent as View).height == idleKeyboardHeight) }
            screenshot("26-wubi-single-header")
            click("退格"); checkCandidateArea(false)
            click("a"); await { findButton("工") != null }; click("工")
            await { editor.text.toString() == "工" }; checkCandidateArea(false)
            runOnMainSync { editor.setText("") }
            passed += "wubi_header_toolbar_candidate_exclusive_fixed_height_and_reset"
            val noMatch = LocalStore(targetContext).use { personal -> WubiRepository(targetContext, personal).use { repo -> listOf("yyyy", "abcd", "qwer", "yxyx", "xxyy").first { repo.lookup(it).isEmpty() } } }
            noMatch.forEach { click(it.toString()) }
            await { findText("编码：$noMatch") != null && findText("无匹配，请退格修正") != null }
            checkCandidateArea(true)
            click("换行"); await { editor.text.toString() == noMatch }; checkCandidateArea(false)
            runOnMainSync { editor.setText("") }
            passed += "unmatched_code_keeps_single_candidate_header_until_resolved"
            chooseLanguage(InputLanguage.ENGLISH); await { findButton("␣ English") != null }
            "app".forEach { click(it.toString()) }; await { findText("英文：app") != null }
            checkCandidateArea(true)
            screenshot("27-english-single-header")
            click("换行"); checkCandidateArea(false)
            click("自动拼写已开启"); await { findButton("自动拼写已关闭") != null }
            "xyz".forEach { click(it.toString()) }; checkCandidateArea(false)
            check(findButton("剪贴板") != null)
            screenshot("28-english-direct-toolbar")
            click("自动拼写已关闭"); await { findButton("自动拼写已开启") != null }
            "qzz".forEach { click(it.toString()) }; await { findText("英文：qzz") != null }; checkCandidateArea(true)
            repeat(3) { click("退格") }; checkCandidateArea(false)
            chooseLanguage(InputLanguage.WUBI); await { findButton("␣ 五笔86") != null }
            runOnMainSync { editor.setText("") }
            passed += "english_header_follows_composition_and_spelling_toggle"
            checkNumberRow(true)
            "1234567890".forEach { click(it.toString()) }
            await { editor.text.toString() == "1234567890" }
            runOnMainSync { "qwertyuiop".forEach { check(!findKeyboardButton(it).isLongClickable) } }
            passed += "number_row_default_order_wubi_input_and_removed_long_press_digits"
            chooseLanguage(InputLanguage.ENGLISH); await { findButton("␣ English") != null }
            checkNumberRow(true)
            "1234567890".forEach { click(it.toString()) }
            click("z"); click("换行")
            await { editor.text.toString() == "12345678901234567890z" }
            chooseLanguage(InputLanguage.WUBI); await { findButton("␣ 五笔86") != null }
            runOnMainSync { editor.setText("") }
            setToggle("显示顶部数字行", false)
            checkNumberRow(false)
            LocalStore(targetContext).use { check(it.get("number_row", "true") == "false") }
            chooseLanguage(InputLanguage.ENGLISH); await { findButton("␣ English") != null }; checkNumberRow(false)
            chooseLanguage(InputLanguage.WUBI); await { findButton("␣ 五笔86") != null }
            setToggle("显示顶部数字行", true)
            checkNumberRow(true)
            passed += "number_row_setting_persists_both_modes"
            setToggle("字母键显示字根提示", true)
            click("a"); await { findText("编码：a") != null }
            runOnMainSync { check(findKeyboardButton('q').performLongClick()) }
            await { findText("Q 键 · 五笔86字根") != null }
            runOnMainSync {
                val grid = windows().filterIsInstance<android.widget.GridLayout>().first { visible(it) && it.contentDescription == "完整字根网格" }
                check(grid.childCount == 15)
                check(editor.text.isEmpty())
                val card = windows().first { visible(it) && it.contentDescription == "Q 键完整字根" }
                val cardLocation = IntArray(2).also { card.getLocationOnScreen(it) }
                val keyLocation = IntArray(2).also { findKeyboardButton('q').getLocationOnScreen(it) }
                check(cardLocation[1] + card.height <= keyLocation[1]) { "Root card must appear above the pressed key" }
            }
            screenshot("19-q-full-roots")
            click("关闭字根卡片")
            await { findText("Q 键 · 五笔86字根") == null }
            await { findText("编码：a") != null }
            click("换行"); await { editor.text.toString() == "a" }
            var rootX = 0f; var rootY = 0f
            runOnMainSync {
                val key = findKeyboardButton('n')
                val location = IntArray(2).also { key.getLocationOnScreen(it) }
                rootX = location[0] + key.width / 2f; rootY = location[1] + key.height / 2f
            }
            val rootTouchTime = SystemClock.uptimeMillis()
            sendPointerSync(android.view.MotionEvent.obtain(rootTouchTime, rootTouchTime, android.view.MotionEvent.ACTION_DOWN, rootX, rootY, 0))
            SystemClock.sleep(android.view.ViewConfiguration.getLongPressTimeout().toLong() + 150)
            sendPointerSync(android.view.MotionEvent.obtain(rootTouchTime, SystemClock.uptimeMillis(), android.view.MotionEvent.ACTION_UP, rootX, rootY, 0))
            await { findText("N 键 · 五笔86字根") != null }
            runOnMainSync {
                check(windows().filterIsInstance<android.widget.GridLayout>().first { visible(it) && it.contentDescription == "完整字根网格" }.childCount == 24)
                check(editor.text.toString() == "a")
            }
            screenshot("20-n-full-roots")
            click("1"); await { findText("N 键 · 五笔86字根") == null && editor.text.toString() == "a1" }
            passed += "root_cards_complete_shapes_no_input_preserve_code_close_on_typing"
            click("a"); await { findText("编码：a") != null }
            for ((key, expected) in mapOf(
                'l' to listOf("田", "甲", "囗", "四", "皿", "罒", "⺲", "曾", "柬", "车", "車", "力", "卌", "冊"),
                'o' to listOf("火", "业", "兼", "灬", "亦", "米"),
                'p' to listOf("之", "⻌", "辶", "廴", "冖", "宀", "\uE000"),
                'b' to listOf("子", "孑", "了", "也", "耳", "巜", "阝", "卩", "⺋", "服", "卫", "予", "凵")
            )) {
                runOnMainSync { check(findKeyboardButton(key).performLongClick()) }
                await { findText("${key.uppercaseChar()} 键 · 五笔86字根") != null }
                runOnMainSync {
                    val grid = windows().filterIsInstance<android.widget.GridLayout>().first { visible(it) && it.contentDescription == "完整字根网格" }
                    val cells = (0 until grid.childCount).map { grid.getChildAt(it) as android.widget.TextView }
                    check(cells.map { it.text.toString() } == expected)
                    check(cells.all { android.text.TextPaint().apply { typeface = it.typeface }.hasGlyph(it.text.toString()) })
                    if (key == 'p') check(cells.last().contentDescription == "P 键字根：礻去末点，衤去末两笔")
                    check(editor.text.toString() == "a1")
                }
                screenshot("43-corrected-roots-$key")
                click("关闭字根卡片")
                await { findText("${key.uppercaseChar()} 键 · 五笔86字根") == null }
                await { findText("编码：a") != null }
            }
            click("退格"); await { findText("编码：a") == null }
            check(editor.text.toString() == "a1")
            passed += "corrected_blop_roots_glyphs_preserve_composition_and_body"
            chooseLanguage(InputLanguage.ENGLISH); await { findButton("␣ English") != null }
            runOnMainSync { check(!findKeyboardButton('q').isLongClickable) }
            chooseLanguage(InputLanguage.WUBI); await { findButton("␣ 五笔86") != null }
            setToggle("字母键显示字根提示", false)
            runOnMainSync { check(!findKeyboardButton('q').isLongClickable) }
            click("Z / 查读音")
            await { findText("编码：z") != null }
            checkCandidateArea(true)
            runOnMainSync {
                val lookup = windows().filterIsInstance<Button>().first { visible(it) && it.contentDescription == "Z / 查读音" }
                check(lookup.text.isEmpty() && lookup.foreground != null)
                check(editor.text.toString() == "a1")
            }
            click("换行"); await { editor.text.toString() == "a1z" }
            checkCandidateArea(false)
            passed += "root_switch_gates_long_press_and_wubi_lookup_icon_keeps_english_z"
            runOnMainSync { editor.setText("") }
            refreshKeyboard()
            click("t"); await { findText("编码：t") != null }
            click("f"); await { findText("编码：tf") != null }
            click("Z / 查读音")
            await { findButton("行，读音：$xingReading") != null }
            checkCandidateArea(true)
            runOnMainSync {
                check(findButtonOnMain("Z / 查读音").isSelected)
                check(editor.text.isEmpty())
            }
            screenshot("28-offline-polyphone-readings")
            click("Z / 查读音"); await { findButton("行") != null }
            runOnMainSync { check(!findButtonOnMain("Z / 查读音").isSelected) }
            click("Z / 查读音"); await { findButton("行，读音：$xingReading") != null }
            click("行，读音：$xingReading"); await { editor.text.toString() == "行" }
            checkCandidateArea(false)
            runOnMainSync { check(!findButtonOnMain("Z / 查读音").isSelected) }
            passed += "lookup_z_toggle_exact_candidate_readings_direct_select_and_reset_ui"
            click("q"); click("v"); await { findText("编码：qv") != null }
            click("Z / 查读音"); click("t"); click("f")
            await { findButton("银行，读音：yín háng") != null }
            screenshot("29-offline-phrase-readings")
            click("银行，读音：yín háng"); await { editor.text.toString() == "行银行" }
            click("t"); click("f"); await { findText("编码：tf") != null }
            click("Z / 查读音"); await { findButton("行，读音：$xingReading") != null }
            var swipeLeft = 0f; var swipeRight = 0f; var swipeY = 0f
            runOnMainSync {
                val scroll = windows().filterIsInstance<android.widget.HorizontalScrollView>().first { visible(it) && it.contentDescription == "读音候选，左右滑动查看" }
                val position = IntArray(2).also { scroll.getLocationOnScreen(it) }
                swipeLeft = position[0] + 10f; swipeRight = position[0] + scroll.width - 10f
                swipeY = position[1] + scroll.height / 2f
            }
            val swipeStart = SystemClock.uptimeMillis()
            sendPointerSync(android.view.MotionEvent.obtain(swipeStart, swipeStart, android.view.MotionEvent.ACTION_DOWN, swipeRight, swipeY, 0))
            for (step in 1..10) {
                SystemClock.sleep(16)
                sendPointerSync(android.view.MotionEvent.obtain(swipeStart, SystemClock.uptimeMillis(), android.view.MotionEvent.ACTION_MOVE, swipeRight + (swipeLeft - swipeRight) * step / 10, swipeY, 0))
            }
            sendPointerSync(android.view.MotionEvent.obtain(swipeStart, SystemClock.uptimeMillis(), android.view.MotionEvent.ACTION_UP, swipeLeft, swipeY, 0))
            await {
                var scrolled = false
                runOnMainSync { scrolled = windows().filterIsInstance<android.widget.HorizontalScrollView>().any { visible(it) && it.contentDescription == "读音候选，左右滑动查看" && it.scrollX > 0 } }
                scrolled
            }
            runOnMainSync { check(editor.text.toString() == "行银行") }
            passed += "reading_row_real_swipe_without_accidental_candidate_commit_ui"
            click("展开候选"); await { findButton("收起候选") != null }
            runOnMainSync {
                val grid = windows().filterIsInstance<android.widget.GridLayout>().first { visible(it) && allViews(it).filterIsInstance<Button>().any { b -> b.text.toString() == "行\n$xingReading" } }
                check(grid.columnCount == 2)
                check(allViews(grid).filterIsInstance<Button>().any { it.contentDescription == "行，读音：$xingReading" })
            }
            screenshot("30-expanded-readings")
            click("行，读音：$xingReading"); await { editor.text.toString() == "行银行行" }
            checkCandidateArea(false)
            passed += "lookup_phrase_reading_in_row_and_existing_expanded_grid_ui"
            click("w"); await { findText("编码：w") != null }
            click("Z / 查读音"); click("q"); click("i"); click("n")
            await { findButton("您，读音：nín") != null }
            runOnMainSync { check(editor.text.toString() == "行银行行") }
            click("a"); await { findText("编码：wqin") != null }
            runOnMainSync { check(editor.text.toString() == "行银行行") }
            click("您，读音：nín"); await { editor.text.toString() == "行银行行您" }
            passed += "lookup_suspends_unique_four_code_and_fifth_code_until_manual_choice_ui"
            click("a"); click("Z / 查读音"); await { findButton("Z / 查读音")?.isSelected == true }
            click("⌫"); checkCandidateArea(false)
            runOnMainSync { check(!findButtonOnMain("Z / 查读音").isSelected) }
            click("Z / 查读音"); await { findText("编码：z") != null }
            click("Z / 查读音"); await { findText("暂无候选可查读音") != null }
            runOnMainSync { check(findTextOnMain("编码：z") != null && !findButtonOnMain("Z / 查读音").isSelected) }
            click("⌫"); checkCandidateArea(false)
            passed += "lookup_delete_to_idle_and_no_match_z_preserves_code_ui"
            chooseLanguage(InputLanguage.ENGLISH); await { findButton("␣ English") != null }
            click("z"); await { editor.text.toString() == "行银行行您z" }
            click("换行"); chooseLanguage(InputLanguage.WUBI); await { findButton("␣ 五笔86") != null }
            passed += "english_z_keeps_normal_letter_semantics_ui"
            runOnMainSync { editor.setText("") }
            click("a"); await { findText("编码：a") != null }
            click("换行"); await { editor.text.toString() == "a" }
            click("换行"); await { editor.text.toString() == "a\n" }
            passed += "return_raw_code_then_newline_ui"
            fun setEnterAction(action: WubiEnterAction) {
                runOnMainSync { enterSpinner.setSelection(WubiEnterAction.entries.indexOf(action)) }
                await { LocalStore(targetContext).use { it.get("wubi_enter_action", "") == action.value } }
                refreshKeyboard()
            }
            setEnterAction(WubiEnterAction.CLEAR_CODE)
            click("Z / 查读音"); await { findText("编码：z") != null }
            click("换行"); checkCandidateArea(false)
            runOnMainSync { check(editor.text.toString() == "a\n") }
            click("a"); await { findText("编码：a") != null }
            click("换行"); checkCandidateArea(false)
            runOnMainSync { check(editor.text.toString() == "a\n") }
            click("换行"); await { editor.text.toString() == "a\n\n" }
            passed += "return_clear_code_preserves_editor_then_newline_ui"
            setEnterAction(WubiEnterAction.FIRST_CANDIDATE)
            click("Z / 查读音"); await { findText("编码：z") != null }
            click("换行"); await { findText("编码：z") != null }
            runOnMainSync { check(editor.text.toString() == "a\n\n") }
            click("⌫"); checkCandidateArea(false)
            click("a"); await { findText("编码：a") != null }
            click("换行"); await { editor.text.toString() == "a\n\n工" }
            val missingCode = LocalStore(targetContext).use { personal -> WubiRepository(targetContext, personal).use { repository ->
                listOf("yyyy", "abcd", "qwer", "yxyx", "xxyy").first { repository.lookup(it).isEmpty() }
            } }
            missingCode.forEach { click(it.toString()) }
            await { findText("无匹配，请退格修正") != null }
            click("换行"); await { findText("无匹配，请退格修正") != null }
            runOnMainSync { check(editor.text.toString() == "a\n\n工") }
            click("⌫"); await { findText("编码：${missingCode.dropLast(1)}") != null }
            passed += "return_first_candidate_and_missing_code_correction_ui"
            setEnterAction(WubiEnterAction.COMMIT_CODE)
            runOnMainSync { editor.setText("") }
            click("切换大小写"); await { findButton("␣ English") != null }
            click("a"); await { editor.text.toString() == "A" }
            click("切换大小写"); awaitKeyCase(false)
            click("b"); await { editor.text.toString() == "Ab" }
            click("切换大小写"); awaitKeyCase(true)
            click("c"); await { editor.text.toString() == "AbC" }
            click("换行")
            chooseLanguage(InputLanguage.WUBI); await { findButton("␣ 五笔86") != null }
            passed += "shift_upper_lower_mode_return_ui"
            click("d"); await { findText("编码：d") != null }
            click("切换大小写"); await { findButton("␣ English") != null && editor.text.toString() == "AbCd" }
            click("a"); await { editor.text.toString() == "AbCdA" }
            click("换行")
            chooseLanguage(InputLanguage.WUBI); await { findButton("␣ 五笔86") != null }
            passed += "shift_pending_code_as_raw_letters_ui"
            runOnMainSync { editor.setText("") }
            screenshot("01-keyboard")
            checkCandidateArea(false)
            InputLanguage.entries.forEach { language ->
                chooseLanguage(language)
                runOnMainSync { check(findButtonOnMain("空格").text.toString() == "␣ ${language.label}") }
                click("符号")
                await { findButton("返回键盘") != null }
                checkCandidateArea(false)
                runOnMainSync {
                    val space = findButtonOnMain("空格")
                    check(space.text.toString() == "␣" && space.stateDescription == null)
                }
                val beforeSpace = editor.text.toString()
                click("空格"); await { editor.text.toString() == beforeSpace + " " }
                if (language == InputLanguage.WUBI) screenshot("35-symbol-space-0.3.1")
                click("返回键盘")
                await { findButton("␣ ${language.label}") != null }
            }
            passed += "space_language_label_only_on_keyboard_symbol_space_in_all_languages"
            InputLanguage.entries.forEach { language ->
                chooseLanguage(language)
                val comma = if (language == InputLanguage.WUBI) "，" else ","
                val period = if (language == InputLanguage.WUBI) "。" else "."
                val exclamation = if (language == InputLanguage.WUBI) "！" else "!"
                val question = if (language == InputLanguage.WUBI) "？" else "?"
                runOnMainSync { editor.setText("") }
                await {
                    var correct = false
                    runOnMainSync {
                        correct = listOf(comma to exclamation, period to question).all { (primary, alternate) ->
                            val button = windows().filterIsInstance<Button>().firstOrNull {
                                visible(it) && it.contentDescription == primary
                            }
                            val layout = button?.layout
                            button?.text.toString() == "$alternate\n$primary" &&
                                button?.stateDescription == "长按输入$alternate" && layout != null &&
                                layout.lineCount == 2 && layout.getLineTop(0) < layout.getLineTop(1) &&
                                layout.getEllipsisCount(0) == 0 && layout.getEllipsisCount(1) == 0
                        }
                    }
                    correct
                }
                click(comma); click(period)
                await { editor.text.toString() == comma + period }
                longPress(comma); await { editor.text.toString() == comma + period + exclamation }
                longPress(period); await { editor.text.toString() == comma + period + exclamation + question }
                screenshot(when (language) {
                    InputLanguage.WUBI -> "36-punctuation-long-press-0.3.1"
                    InputLanguage.ENGLISH -> "37-english-punctuation-0.3.1"
                    InputLanguage.KOREAN -> "38-korean-punctuation-0.3.1"
                })
            }
            passed += "all_languages_punctuation_hints_above_primary_short_tap_and_real_long_press"
            chooseLanguage(InputLanguage.ENGLISH)
            runOnMainSync { editor.setText("") }
            click("a"); await { editor.text.toString() == "a" }
            longPress(","); await { editor.text.toString() == "a!" }
            click("b"); await { editor.text.toString() == "a!b" }
            longPress("."); await { editor.text.toString() == "a!b?" }
            chooseLanguage(InputLanguage.KOREAN)
            runOnMainSync { editor.setText("") }
            runOnMainSync { findKeyboardButton('r').performClick(); findKeyboardButton('k').performClick() }
            await { editor.text.toString() == "가" }
            longPress(","); await { editor.text.toString() == "가!" }
            runOnMainSync { findKeyboardButton('s').performClick(); findKeyboardButton('k').performClick() }
            await { editor.text.toString() == "가!나" }
            longPress("."); await { editor.text.toString() == "가!나?" }
            passed += "english_and_korean_long_press_confirms_composition_before_ascii_punctuation"
            chooseLanguage(InputLanguage.WUBI)
            runOnMainSync { editor.setText("") }
            click("a"); await { findText("编码：a") != null }
            longPress("，"); await { editor.text.toString() == "工！" && findText("编码：a") == null }
            runOnMainSync { editor.setText("") }
            click("Z / 查读音"); await { findText("编码：z") != null }
            longPress("。")
            runOnMainSync { check(editor.text.isEmpty()) }
            await { findText("编码：z") != null }
            click("退格"); await { findText("编码：z") == null }
            runOnMainSync { check(editor.text.isEmpty()) }
            passed += "punctuation_long_press_resolves_candidate_and_preserves_unmatched_code"
            click("123"); await { findButton("返回") != null }
            checkCandidateArea(false)
            runOnMainSync {
                val plus = windows().filterIsInstance<Button>().first { visible(it) && it.text.toString() == "+" }
                val grid = plus.parent.parent as android.widget.LinearLayout
                check((0..3).map { index -> ((grid.getChildAt(index) as android.widget.LinearLayout).getChildAt(3) as Button).text.toString() } == listOf("+", "-", "*", "/"))
                val bottom = grid.getChildAt(4) as android.widget.LinearLayout
                check(bottom.childCount == 4 && (bottom.getChildAt(0) as Button).text.toString() == "返回" && (bottom.getChildAt(1) as Button).text.toString() == "_")
                val zero = (grid.getChildAt(3) as android.widget.LinearLayout).getChildAt(1)
                check(zero.left == bottom.getChildAt(1).left && zero.width == bottom.getChildAt(1).width)
            }
            listOf("+", "-", "*", "/", "_").forEach { click(it) }
            await { editor.text.toString() == "+-*/_" }
            screenshot("23-number-panel")
            click("返回"); await { findButton("简") != null }
            chooseLanguage(InputLanguage.ENGLISH); await { findButton("␣ English") != null }
            click("123"); click("返回"); await { findButton("␣ English") != null }
            chooseLanguage(InputLanguage.WUBI); await { findButton("␣ 五笔86") != null }
            runOnMainSync { editor.setText("") }
            passed += "number_panel_return_operator_column_underscore_alignment_both_modes"
            checkCandidateArea(false)
            check(findButton("展开候选") == null)
            click("a"); await { findButton("工") != null }
            await { findButton("展开候选") != null }
            screenshot("02-wubi-candidates")
            click("工")
            await { editor.text.toString() == "工" && findText("编码：a") == null }
            check(findButton("收起候选") == null && findButton("简") != null)
            screenshot("18-wubi-direct-selection")
            passed += "wubi_overflow_visible_candidate_direct_commit_ui"
            runOnMainSync { editor.setText("") }
            click("a"); await { findButton("展开候选") != null }
            click("展开候选"); await { findButton("收起候选") != null }
            check(editor.text.isEmpty())
            click("收起候选"); await { findButton("展开候选") != null }
            click("展开候选"); await { findButton("收起候选") != null }
            screenshot("09-expanded-candidates")
            click("工", last = true)
            await { editor.text.toString() == "工" }
            passed += "candidate_arrow_expand_collapse_select_grid_ui"
            passed += "wubi_input_ui"
            chooseLanguage(InputLanguage.ENGLISH); await { findButton("␣ English") != null }
            click("a"); await { editor.text.toString() == "工a" }
            passed += "latin_input_ui"
            click("换行") // Finish the current English word to restore the toolbar.
            chooseLanguage(InputLanguage.WUBI); await { findButton("简") != null }
            runOnMainSync { editor.setText("") }
            click("v"); await { findText("编码：v") != null }
            click("简"); await { findButton("繁") != null && findButton("發") != null }
            check(editor.text.isEmpty())
            click("發")
            await { editor.text.toString() == "發" && findText("编码：v") == null }
            check(findButton("收起候选") == null && findButton("繁") != null)
            passed += "traditional_overflow_visible_candidate_direct_commit_ui"
            click("退格"); await { editor.text.isEmpty() }
            click("v"); await { findButton("展开候选") != null }
            click("展开候选"); await { findButton("髮") != null }
            screenshot("14-traditional-variants")
            click("髮", last = true); await { editor.text.toString() == "髮" }
            passed += "traditional_realtime_toggle_all_variants_select_ui"
            "ntcy".forEach { click(it.toString()) }
            await { findText("编码：ntcy") != null }
            check(editor.text.toString() == "髮")
            click("空格"); await { editor.text.toString() == "髮發" }
            click("繁"); await { findButton("简") != null }
            click("v"); click("空格"); await { editor.text.toString() == "髮發发" }
            passed += "traditional_ambiguous_four_code_no_auto_commit_and_simplified_restore_ui"
            runOnMainSync { editor.setText("前文 "); editor.setSelection(editor.length()) }
            await { findButton("简") != null }
            chooseLanguage(InputLanguage.ENGLISH); await { findButton("自动拼写已开启") != null }
            val spellingKey = findButton("自动拼写已开启")!!
            runOnMainSync {
                check(spellingKey.text.isEmpty() && spellingKey.foreground != null && spellingKey.isSelected)
            }
            "app".forEach { click(it.toString()) }
            await { findText("英文：app") != null && findButton("展开候选") != null }
            check(editor.text.toString() == "前文 app")
            screenshot("16-english-spelling-key")
            click("展开候选"); await { findButton("apple") != null }
            screenshot("15-english-completion")
            click("apple", last = true); await { editor.text.toString() == "前文 apple" }
            click("空格"); await { editor.text.toString() == "前文 apple " }
            passed += "english_prefix_overflow_icon_and_composing_replacement_ui"
            "app".forEach { click(it.toString()) }
            await { findText("英文：app") != null && findButton("展开候选") != null }
            click("app")
            await { findText("英文：app") == null && findButton("展开候选") == null && findButton("收起候选") == null }
            check(editor.text.toString() == "前文 apple app" && findButton("自动拼写已开启") != null)
            click("空格")
            "appl".forEach { click(it.toString()) }
            await { findText("英文：appl") != null && findButton("apple") != null && findButton("展开候选") != null }
            click("apple")
            await { editor.text.toString() == "前文 apple app apple" && findText("英文：appl") == null }
            check(findButton("收起候选") == null && findButton("自动拼写已开启") != null)
            screenshot("17-english-direct-selection")
            click("空格")
            passed += "english_visible_overflow_candidates_direct_commit_keep_keyboard_ui"
            "abandonment".forEach { click(it.toString()) }
            await { findText("英文：abandonment") != null && findButton("abandonment") != null }
            check(findButton("展开候选") == null)
            click("abandonment"); click("空格")
            await { editor.text.toString() == "前文 apple app apple abandonment " }
            passed += "english_single_candidate_no_expand_ui"
            "qzz".forEach { click(it.toString()) }
            await { findText("英文：qzz") != null }
            click("退格"); await { findText("英文：qz") != null }
            click("换行"); await { findText("英文：qz") == null && editor.text.toString().endsWith("qz") }
            click("换行"); await { editor.text.toString().endsWith("qz\n") }
            passed += "english_unknown_word_delete_and_literal_enter_ui"
            "app".forEach { click(it.toString()) }; await { findText("英文：app") != null }
            click("自动拼写已开启"); await { findButton("自动拼写已关闭") != null }
            click("l"); await { editor.text.toString().endsWith("appl") }
            check(findButton("展开候选") == null && findText("英文：appl") == null)
            click("自动拼写已关闭"); await { findButton("自动拼写已开启") != null }
            "app".forEach { click(it.toString()) }; await { findText("英文：app") != null }
            runOnMainSync { editor.setSelection(0) }
            await { findText("英文：app") == null && findButton("展开候选") == null }
            click("b"); await { editor.text.toString().startsWith("b前文") }
            click("换行")
            chooseLanguage(InputLanguage.WUBI); await { findButton("简") != null }
            passed += "english_toggle_commits_raw_and_cursor_move_ends_composition_ui"
            runOnMainSync { editor.setText("") }
            waitForIdleSync()
            click("切换大小写"); await { findButton("␣ English") != null }
            "app".forEach { click(it.toString()) }
            await { findText("英文：APP") != null }
            click("展开候选"); click("APPLE", last = true)
            await { editor.text.toString() == "APPLE" }
            click("空格"); click("a")
            await { findText("英文：A") != null }
            click("切换大小写"); await {
                var lower = false
                runOnMainSync { lower = findKeyboardButton('p').text.toString() == "p" }
                lower
            }
            click("p"); click("p"); await { findText("英文：App") != null }
            click("展开候选"); click("Apple", last = true)
            await { editor.text.toString() == "APPLE Apple" }
            click("空格"); await { editor.text.toString() == "APPLE Apple " }
            passed += "english_uppercase_titlecase_candidates_ui"
            // Queue a burst on one main-thread turn, including an intervening space.
            runOnMainSync {
                "apple app".forEach { char ->
                    val label = if (char == ' ') "空格" else char.toString()
                    windows().filterIsInstance<Button>().first { visible(it) && it.contentDescription?.toString() == label }.performClick()
                }
            }
            await { findText("英文：app") != null && editor.text.toString() == "APPLE Apple apple app" }
            click("空格"); await { editor.text.toString() == "APPLE Apple apple app " }
            passed += "english_fast_queued_typing_keeps_words_and_spaces_ui"
            val originalInputType = editor.inputType
            runOnMainSync {
                editor.setText("")
                editor.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                manager.restartInput(editor)
            }
            await { findButton("自动拼写已关闭") != null }
            checkCandidateArea(false)
            check(!findButton("自动拼写已关闭")!!.isEnabled)
            "app".forEach { click(it.toString()) }
            await { editor.text.toString() == "app" }
            check(findButton("展开候选") == null && findText("英文：app") == null)
            passed += "english_password_disables_completion_ui"
            runOnMainSync { editor.inputType = originalInputType; editor.setText(""); manager.restartInput(editor) }
            await { findButton("切换语言") != null }
            chooseLanguage(InputLanguage.WUBI)
            await { findButton("简") != null && findButton("␣ 五笔86") != null }
            runOnMainSync { editor.setText("工a"); editor.setSelection(editor.length()) }
            click("表情"); await { findButton("😀") != null && findButton("‹ 键盘") != null }
            checkCandidateArea(false)
            check(findButton("收藏") == null && findButton("‹") == null && findButton("›") == null)
            passed += "emoji_common_single_page_no_favorites_ui"
            runOnMainSync {
                val categories = windows().filterIsInstance<EmojiCategoryStrip>().first { visible(it) }
                check(!categories.isHorizontalScrollBarEnabled && !categories.isVerticalScrollBarEnabled)
                check(categories.overScrollMode == View.OVER_SCROLL_NEVER)
                categories.scrollTo(categories.getChildAt(0).width, 0)
            }
            await { var x = 0; runOnMainSync { x = windows().filterIsInstance<EmojiCategoryStrip>().first { visible(it) }.scrollX }; x > 0 }
            var categoryX = 0
            runOnMainSync { categoryX = windows().filterIsInstance<EmojiCategoryStrip>().first { visible(it) }.scrollX }
            click("旗帜")
            await {
                var restored = false
                runOnMainSync {
                    restored = windows().filterIsInstance<EmojiCategoryStrip>().first { visible(it) }.let { it.isLaidOut && !it.isLayoutRequested && it.scrollX == categoryX }
                }
                restored
            }
            click("›")
            await { var same = false; runOnMainSync { same = windows().filterIsInstance<EmojiCategoryStrip>().first { visible(it) }.scrollX == categoryX }; same }
            screenshot("10-emoji-category-position")
            passed += "emoji_category_no_scrollbar_retains_position"
            click("🇨🇳"); await { editor.text.toString() == "工a🇨🇳" }
            click("🇨🇳"); await { editor.text.toString() == "工a🇨🇳🇨🇳" }
            LocalStore(targetContext).use { check(it.emojiUsage()["🇨🇳"]?.count == 2L) }
            runOnMainSync { windows().filterIsInstance<EmojiCategoryStrip>().first { visible(it) }.scrollTo(0, 0) }
            click("常用")
            await {
                var first = ""
                runOnMainSync { first = windows().filterIsInstance<Button>().firstOrNull { visible(it) && it.parent is android.widget.GridLayout }?.text.toString() }
                first == "🇨🇳"
            }
            check(findButton("‹") == null && findButton("›") == null)
            screenshot("11-emoji-common-frequency")
            passed += "emoji_common_ranks_usage_from_other_category_ui"
            click("⌫"); await { editor.text.toString() == "工a🇨🇳" }
            click("⌫"); await { editor.text.toString() == "工a" }
            waitForIdleSync(); SystemClock.sleep(180)
            val emojiButton = findButton("😀")!!
            var emojiX = 0f; var emojiY = 0f
            runOnMainSync { val rect = android.graphics.Rect(); check(emojiButton.getGlobalVisibleRect(rect)); emojiX = rect.exactCenterX(); emojiY = rect.exactCenterY(); check(!emojiButton.isLongClickable) }
            val holdTime = SystemClock.uptimeMillis()
            sendPointerSync(android.view.MotionEvent.obtain(holdTime, holdTime, android.view.MotionEvent.ACTION_DOWN, emojiX, emojiY, 0))
            SystemClock.sleep(700)
            runOnMainSync { check(emojiButton.isAttachedToWindow && editor.text.toString() == "工a") }
            sendPointerSync(android.view.MotionEvent.obtain(holdTime, SystemClock.uptimeMillis(), android.view.MotionEvent.ACTION_UP, emojiX, emojiY, 0))
            waitForIdleSync()
            if (editor.text.toString() == "工a") click("😀", last = true)
            await { editor.text.toString() == "工a😀" }
            LocalStore(targetContext).use { check(it.emojiUsage()["😀"]?.count == 1L) }
            runOnMainSync { check(emojiButton.isAttachedToWindow) }
            check(findButton("收藏这个表情") == null)
            passed += "emoji_long_press_no_popup_stable_grid_one_insert"
            passed += "emoji_input_ui"
            click("⌫"); await { editor.text.toString() == "工a" }
            passed += "emoji_delete_ui"
            click("五笔搜索 emoji")
            await { findButton("关键词：输入中文") != null }
            checkCandidateArea(false)
            longPress("，"); await { findButton("关键词：！") != null }
            longPress("。"); await { findButton("关键词：！？") != null }
            runOnMainSync { check(editor.text.toString() == "工a") }
            click("清空"); await { findButton("关键词：输入中文") != null }
            passed += "punctuation_long_press_emoji_search_target_isolation"
            listOf("t", "t", "d", "u").forEach { click(it) }
            await { findText("搜索编码：ttdu") != null }
            await { findButton("笑") != null }
            click("笑") // ttdu has several exact entries; respect manual selection.
            await { findButton("关键词：笑") != null }
            check(editor.text.toString() == "工a") { "Emoji search escaped into host editor" }
            passed += "emoji_search_isolation_ui"
            screenshot("03-emoji-search")
            click("‹ 键盘"); await { findButton("简") != null }
            checkCandidateArea(false)
            runOnMainSync { editor.append("👨‍👩‍👧‍👦") }
            click("⌫"); await { editor.text.toString() == "工a" }
            runOnMainSync { editor.append("👍🏽") }
            click("⌫"); await { editor.text.toString() == "工a" }
            runOnMainSync { editor.append("🇨🇳") }
            click("⌫"); await { editor.text.toString() == "工a" }
            passed += "complex_emoji_delete_ui"
            InputLanguage.entries.forEach { language ->
                chooseLanguage(language)
                runOnMainSync { editor.setText("") }; refreshKeyboard()
                click("符号"); await { findButton("返回键盘") != null }
                reveal("……"); tap("……")
                await { editor.text.toString() == "……" && findButton("␣ ${language.label}") != null }
                check(findButton("返回键盘") == null)
                checkCandidateArea(false)
            }
            chooseLanguage(InputLanguage.WUBI)
            screenshot("42-symbol-auto-return-keyboard-0.3.3")
            passed += "symbols_auto_return_all_languages_whole_multicharacter_symbol_and_original_language"
            runOnMainSync { editor.setText("") }; refreshKeyboard()
            click("符号"); await { findButton("返回键盘") != null }
            reveal("数学"); click("数学")
            check(findButton("返回键盘") != null)
            click("空格"); await { editor.text.toString() == " " }
            check(findButton("返回键盘") != null)
            click("退格"); await { editor.text.isEmpty() }
            check(findButton("返回键盘") != null)
            click("换行"); await { editor.text.toString() == "\n" }
            check(findButton("返回键盘") != null)
            click("返回键盘"); await { findButton("␣ 五笔86") != null }
            passed += "symbol_category_space_delete_and_enter_do_not_auto_return"
            setToggle("输入符号后返回键盘", false)
            LocalStore(targetContext).use { check(it.get("symbol_auto_return", "true") == "false") }
            InputLanguage.entries.forEach { language ->
                chooseLanguage(language)
                runOnMainSync { editor.setText("") }; refreshKeyboard()
                click("符号"); await { findButton("返回键盘") != null }
                reveal("英文"); click("英文")
                reveal("\$"); click("\$"); reveal("^"); click("^")
                await { editor.text.toString() == "\$^" }
                runOnMainSync { check(windows().filterIsInstance<SymbolPanel>().single { visible(it) }.selectedCategory == "英文") }
                click("返回键盘"); await { findButton("␣ ${language.label}") != null }
            }
            passed += "symbol_auto_return_disabled_persists_and_all_languages_keep_category_for_continuous_input"
            for (enabled in listOf(false, true)) {
                setToggle("输入符号后返回键盘", enabled)
                chooseLanguage(InputLanguage.WUBI)
                runOnMainSync { editor.setText("") }; refreshKeyboard()
                click("表情"); await { findButton("😀") != null }
                click("😀"); click("😀"); await { editor.text.toString() == "😀😀" }
                check(findButton("‹ 键盘") != null && findButton("😀") != null)
                click("‹ 键盘"); await { findButton("␣ 五笔86") != null }
                click("符号"); await { findButton("返回键盘") != null }
                reveal("→"); click("→"); await { editor.text.toString() == "😀😀→" }
                if (enabled) await { findButton("␣ 五笔86") != null }
                else { check(findButton("返回键盘") != null); click("返回键盘"); await { findButton("␣ 五笔86") != null } }
            }
            passed += "emoji_continuous_input_unchanged_in_both_symbol_return_settings_and_reenable_applies"
            setToggle("输入符号后返回键盘", false)
            runOnMainSync { editor.setText("工a"); editor.setSelection(editor.length()) }
            val beforeSymbols = LocalStore(targetContext).use { it.emojiUsage() }
            click("符号")
            checkCandidateArea(false)
            await {
                var common = false
                runOnMainSync { common = windows().filterIsInstance<SymbolPanel>().firstOrNull { visible(it) }?.selectedCategory == "常用" }
                common
            }
            screenshot("12-symbols-common")
            reveal("英文"); click("英文")
            val englishOrder = SymbolCatalog.categories.first { it.name == "英文" }.symbols
            runOnMainSync {
                val grid = windows().filterIsInstance<android.widget.GridLayout>().first { visible(it) && it.contentDescription == "符号网格" }
                check((0 until grid.childCount).map { (grid.getChildAt(it) as Button).text.toString() } == englishOrder)
            }
            reveal("\$"); click("\$")
            reveal("`"); click("`")
            reveal("^"); click("^")
            await { editor.text.toString() == "工a\$`^" }
            passed += "symbols_complete_english_grid_fixed_order_ui"
            reveal("箭头"); click("箭头"); reveal("→"); click("→")
            reveal("数学"); click("数学"); reveal("∞"); click("∞")
            await { editor.text.toString() == "工a\$`^→∞" }
            reveal("中文"); click("中文"); reveal("……"); click("……")
            reveal("单位"); click("单位"); reveal("m²"); click("m²")
            await { editor.text.toString() == "工a\$`^→∞……m²" }
            LocalStore(targetContext).use { check(it.emojiUsage() == beforeSymbols) }
            passed += "symbols_categories_whole_entries_no_frequency_ui"
            click("返回键盘"); await { findButton("简") != null }
            click("符号")
            await {
                var common = false
                runOnMainSync { common = windows().filterIsInstance<SymbolPanel>().firstOrNull { visible(it) }?.selectedCategory == "常用" }
                common
            }
            passed += "symbols_reopen_defaults_to_common_ui"
            reveal("数学"); click("数学"); screenshot("13-symbols-math")
            click("返回键盘")
            await { findButton("简") != null }
            setToggle("输入符号后返回键盘", true)
            runOnMainSync { editor.setText("工a"); editor.setSelection(editor.length()) }
            // Known synthetic data only, on a disposable emulator.
            runOnMainSync { targetContext.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("test", "离线剪贴板测试")) }
            click("剪贴板"); await { findText("离线剪贴板测试") != null }
            checkCandidateArea(false)
            screenshot("04-clipboard")
            click("粘贴到输入框"); await { editor.text.toString() == "工a离线剪贴板测试" }
            passed += "clipboard_preview_paste_ui"
            click("一键清空"); await { findText("剪贴板为空") != null }
            runOnMainSync { check(!targetContext.getSystemService(ClipboardManager::class.java).hasPrimaryClip()) }
            LocalStore(targetContext).use { check(it.clipboardHistory().isEmpty()) }
            passed += "clipboard_clear_ui"
            click("‹ 返回键盘")
            await { findButton("简") != null }
            checkCandidateArea(false)
            passed += "auxiliary_panels_and_idle_keyboard_show_toolbar_without_candidates"
            for (i in 1..12) {
                runOnMainSync { targetContext.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("test", "面板外复制$i")) }
                await { LocalStore(targetContext).use { it.clipboardHistory().firstOrNull()?.text == "面板外复制$i" } }
            }
            passed += "clipboard_record_outside_panel"
            click("剪贴板")
            await { findText("面板外复制11") != null }
            LocalStore(targetContext).use { check(it.clipboardHistory().size == 10) }
            val beforePaste = editor.text.toString()
            click("粘贴历史第1项"); await { editor.text.toString() == beforePaste + "面板外复制11" }
            click("删除历史第1项")
            await { LocalStore(targetContext).use { it.clipboardHistory().none { entry -> entry.text == "面板外复制11" } } }
            passed += "clipboard_history_paste_delete_ui"
            screenshot("06-clipboard-history")
            click("一键清空"); await { findText("剪贴板为空") != null }
            LocalStore(targetContext).use { check(it.clipboardHistory().isEmpty()) }
            passed += "clipboard_clear_current_and_history_ui"
            runOnMainSync {
                val clip = ClipData.newPlainText("sensitive-test", "仅用于模拟器的敏感测试")
                clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
                targetContext.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
            }
            await { findButton("显示内容") != null }
            LocalStore(targetContext).use { check(it.clipboardHistory().isEmpty()) }
            check(findText("仅用于模拟器的敏感测试") == null)
            passed += "sensitive_clipboard_mask_ui"
            screenshot("05-sensitive-clipboard")
            click("显示内容"); await { findText("仅用于模拟器的敏感测试") != null }
            click("一键清空"); await { findText("剪贴板为空") != null }
            passed += "sensitive_clipboard_reveal_clear_ui"
            manager.enabledInputMethodList.firstOrNull { it.packageName != targetContext.packageName }?.let {
                shell("ime set ${it.id}")
                await { findButton("剪贴板") == null }
                shell("ime set ${targetContext.packageName}/.ime.FansonInputMethodService")
                runOnMainSync { manager.showSoftInput(editor, android.view.inputmethod.InputMethodManager.SHOW_FORCED) }
                await { findButton("剪贴板") != null }
                passed += "input_method_service_rebind_lifecycle"
            }
            runOnMainSync { targetContext.getSystemService(android.view.inputmethod.InputMethodManager::class.java).showInputMethodPicker() }
            await {
                val picker = uiAutomation.rootInActiveWindow
                picker != null && picker.packageName?.toString() != targetContext.packageName &&
                    picker.findAccessibilityNodeInfosByText("放松输入法").isNotEmpty()
            }
            check(uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText("五笔86").isEmpty())
            screenshot("08-input-method-picker")
            uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            passed += "system_input_method_picker_label_ui"
            // Last UI scenario: record while hidden without racing a re-show.
            runOnMainSync { targetContext.getSystemService(android.view.inputmethod.InputMethodManager::class.java).hideSoftInputFromWindow(editor.windowToken, 0) }
            await { findButton("剪贴板") == null }
            runOnMainSync { targetContext.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("test", "隐藏键盘复制")) }
            await { LocalStore(targetContext).use { it.clipboardHistory().firstOrNull()?.text == "隐藏键盘复制" } }
            passed += "clipboard_record_keyboard_hidden"
            runOnMainSync { targetContext.getSystemService(ClipboardManager::class.java).clearPrimaryClip() }
            LocalStore(targetContext).use { it.clearClipboardHistory() }
            LocalStore(targetContext).use { it.put("wubi_frequency", "true") }
            runOnMainSync { activity.finish() }
            finish(Activity.RESULT_OK, Bundle().apply { putString("passed", passed.joinToString(",")); putInt("count", passed.size) })
        } catch (error: Throwable) {
            runCatching { screenshot("failure") }
            var uiLabels = ""
            runOnMainSync { uiLabels = windows().filterIsInstance<TextView>().filter { visible(it) }.joinToString(" | ") { it.text.toString() }.take(1600) }
            val imeState = runCatching {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand("dumpsys input_method")).bufferedReader().use { it.readText() }
                    .lineSequence().filter { it.contains("mCurId=") || it.contains("mShowRequested=") || it.contains("mHaveConnection=") || it.contains("mInputShown=") || it.contains("mCurFocusedWindow=") }.joinToString("\n") +
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand("dumpsys activity service com.fanson.ime/.ime.FansonInputMethodService")).bufferedReader().use { it.readText() }.takeLast(4000)
            }.getOrDefault("unavailable")
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("passed", passed.joinToString(",")); putString("failure", error.toString() + "\n" + error.stackTrace.take(8).joinToString("\n")); putString("test_ui", uiLabels); putString("test_ime", imeState) })
        }
    }
    private fun allViews(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { allViews(view.getChildAt(it)) } else emptyList()
    private fun checkHapticSettings(activity: Activity) {
        LocalStore(targetContext).use { store ->
            fun slider() = allViews(activity.window.decorView).filterIsInstance<android.widget.SeekBar>().first { it.contentDescription == "振动强度" }
            fun toggle() = allViews(activity.window.decorView).filterIsInstance<android.widget.Switch>().first { it.text.toString() == "按键轻震动" }
            runOnMainSync {
                check(!toggle().isChecked && !slider().isEnabled)
                check(slider().min == 0 && slider().max == 100 && slider().progress == 50)
                val email = allViews(activity.window.decorView).filterIsInstance<TextView>().single { it.text.toString() == "作者邮箱：cnxfs@foxmail.com" }
                check(email.isTextSelectable)
            }
            passed += "haptic_strength_upgrade_default_and_switch_gate_ui"
            passed += "author_email_exact_and_selectable_in_about_ui"
            runOnMainSync {
                toggle().isChecked = true
                val args = Bundle().apply { putFloat(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 70f) }
                check(slider().performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id, args))
                check(slider().progress == 70 && slider().stateDescription == "70%")
                slider().requestRectangleOnScreen(android.graphics.Rect(0, 0, slider().width, slider().height), true)
            }
            check(store.get("haptic_strength", "missing") == "70")
            var xStart = 0f; var xEnd = 0f; var y = 0f
            var previous: List<Int>? = null; var stableSince = 0L
            await {
                var stable = false
                runOnMainSync {
                    val bar = slider()
                    val position = IntArray(2).also { bar.getLocationOnScreen(it) }.toList() + listOf(bar.width, bar.height)
                    if (position != previous) { previous = position; stableSince = SystemClock.uptimeMillis() }
                    val rect = android.graphics.Rect()
                    stable = bar.isLaidOut && bar.getGlobalVisibleRect(rect) && rect.height() == bar.height && SystemClock.uptimeMillis() - stableSince >= 150
                    if (stable) {
                        val track = bar.width - bar.paddingLeft - bar.paddingRight
                        xStart = position[0] + bar.paddingLeft + track * .7f
                        xEnd = position[0] + bar.paddingLeft + track * .25f
                        y = position[1] + bar.height / 2f
                    }
                }
                stable
            }
            val down = SystemClock.uptimeMillis()
            sendPointerSync(android.view.MotionEvent.obtain(down, down, android.view.MotionEvent.ACTION_DOWN, xStart, y, 0))
            sendPointerSync(android.view.MotionEvent.obtain(down, SystemClock.uptimeMillis(), android.view.MotionEvent.ACTION_MOVE, xEnd, y, 0))
            var moved = 0
            await {
                runOnMainSync { moved = slider().progress }
                moved in 20..30
            }
            check(store.get("haptic_strength", "") == "70") // dragging must not write repeatedly
            sendPointerSync(android.view.MotionEvent.obtain(down, SystemClock.uptimeMillis(), android.view.MotionEvent.ACTION_UP, xEnd, y, 0))
            await { store.get("haptic_strength", "") == moved.toString() }
            screenshot("47-haptic-strength-slider-0.4.1")
            runOnMainSync {
                for (level in listOf(0f, 100f)) {
                    val args = Bundle().apply { putFloat(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, level) }
                    check(slider().performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id, args))
                    check(store.get("haptic_strength", "") == level.toInt().toString())
                }
                toggle().isChecked = false
                check(!slider().isEnabled && slider().progress == 100)
            }
            passed += "haptic_real_slider_drag_defers_save_to_release_and_accessibility_endpoints_ui"
            fun theme(index: Int) {
                var previousSlider: android.widget.SeekBar? = null
                runOnMainSync {
                    previousSlider = slider()
                    allViews(activity.window.decorView).filterIsInstance<android.widget.Spinner>().first { it.getItemAtPosition(0).toString() == "跟随系统" }.setSelection(index)
                }
                await {
                    var rebuilt = false
                    runOnMainSync { rebuilt = slider() !== previousSlider && store.get("theme", "") == if (index == 1) "light" else "system" }
                    rebuilt
                }
            }
            theme(1)
            runOnMainSync { check(slider().progress == 100 && !slider().isEnabled && !toggle().isChecked) }
            LocalStore(targetContext).use { check(it.get("haptic_strength", "") == "100" && it.get("haptic", "") == "false") }
            store.put("haptic_strength", "50")
            theme(0)
            runOnMainSync {
                check(slider().progress == 50 && !slider().isEnabled)
                allViews(activity.window.decorView).filterIsInstance<android.widget.ScrollView>().first().scrollTo(0, 0)
            }
            passed += "haptic_strength_and_off_state_survive_settings_rebuild_and_reopen_ui"
        }
    }
    private fun checkFrequencyUi(activity: Activity, editor: EditText, refresh: () -> Unit) {
        val store = LocalStore(targetContext)
        val frequency = com.fanson.ime.data.WubiFrequencyStore.get(targetContext)
        val model = frequency.model
        val toggle = allViews(activity.window.decorView).filterIsInstance<android.widget.Switch>().first { it.text.toString() == "五笔按本地词频排序" }
        runOnMainSync {
            check(toggle.isChecked)
            for (label in listOf("清空自定义短语", "清空 emoji 使用次数", "清空五笔词频")) {
                check(allViews(activity.window.decorView).filterIsInstance<Button>().count { it.text.toString() == label } == 1)
            }
        }
        passed += "frequency_default_enabled_and_three_independent_clear_buttons_ui"
        store.addPhrase("a", "合成甲"); store.addPhrase("a", "合成乙")
        val fixed = store.phrases("a").first().text
        val chosen = store.phrases("a").last().text
        refresh()
        fun typeA() { runOnMainSync { findKeyboardButton('a').performClick() }; await { findText("编码：a") != null } }
        fun firstCandidate(): String {
            var text = ""
            runOnMainSync {
                val strip = windows().filterIsInstance<CandidateStrip>().first { visible(it) }
                text = allViews(strip).filterIsInstance<Button>().first { visible(it) && it.text.isNotEmpty() }.text.toString()
            }
            return text
        }
        typeA(); await { findButton(chosen) != null }; click(chosen)
        await { editor.text.toString() == chosen }
        check(model.size == 1 && store.frequencyRecords().isEmpty())
        typeA(); check(firstCandidate() == chosen)
        screenshot("45-wubi-frequency-promoted-0.4.0")
        click("空格"); await { editor.text.toString() == chosen + chosen }
        check(store.frequencyRecords().isEmpty())
        passed += "frequency_candidate_click_and_space_promote_in_memory_without_disk_ui"
        click("收起键盘"); await { findButton("退格") == null }
        WubiFrequencyChecks.edit(this) {}
        check(store.frequencyRecords().single().score == 2.0 || store.frequencyRecords().single().score in 1.99..2.0)
        passed += "frequency_actual_hide_button_flushes_successful_learning_ui"
        runOnMainSync { toggle.isChecked = false; editor.setText("") }
        refresh()
        typeA(); check(firstCandidate() == fixed)
        click("空格"); await { editor.text.toString() == fixed }
        check(model.size == 1 && model.pendingSize == 0)
        LocalStore(targetContext).use { check(it.get("wubi_frequency", "true") == "false") }
        passed += "frequency_disabled_uses_fixed_order_stops_learning_and_persists_ui"
        runOnMainSync { toggle.isChecked = true; editor.setText("") }
        refresh(); typeA(); check(firstCandidate() == chosen); click("退格")
        passed += "frequency_reenable_reuses_saved_learning_ui"
        val originalOptions = editor.imeOptions
        runOnMainSync {
            editor.imeOptions = originalOptions or android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            editor.setText("")
        }
        refresh(); typeA(); check(firstCandidate() == fixed); click(chosen)
        await { editor.text.toString() == chosen }
        check(model.pendingSize == 0 && model.size == 1)
        passed += "frequency_no_personalized_learning_field_uses_fixed_order_and_does_not_record_ui"
        runOnMainSync { editor.imeOptions = originalOptions; editor.setText("") }
        refresh()
        click("表情"); click("五笔搜索 emoji")
        await { findButton("关键词：输入中文") != null }
        runOnMainSync { findKeyboardButton('a').performClick() }
        await { findText("搜索编码：a") != null }
        check(firstCandidate() == fixed)
        click(chosen); await { findButton("关键词：$chosen") != null }
        check(editor.text.isEmpty() && model.pendingSize == 0)
        click("‹ 键盘"); await { findButton("简") != null }
        passed += "frequency_emoji_search_uses_fixed_order_without_learning_or_host_commit_ui"
        click("收起键盘"); await { findButton("退格") == null }
        store.recordEmojiUsage("😀")
        fun confirm(label: String) {
            runOnMainSync { allViews(activity.window.decorView).filterIsInstance<Button>().first { it.text.toString() == label }.performClick() }
            click("清空")
        }
        confirm("清空 emoji 使用次数")
        await { store.emojiUsage().isEmpty() }
        check(store.phrases("a").size == 2 && model.size == 1)
        confirm("清空五笔词频")
        await { model.size == 0 && store.frequencyRecords().isEmpty() }
        check(store.phrases("a").size == 2 && toggle.isChecked)
        confirm("清空自定义短语")
        await { store.phrases().isEmpty() }
        runOnMainSync {
            val button = allViews(activity.window.decorView).filterIsInstance<Button>().first { it.text.toString() == "清空五笔词频" }
            button.requestRectangleOnScreen(android.graphics.Rect(0, 0, button.width, button.height), true)
        }
        SystemClock.sleep(3500) // Visual evidence: let Android's transient completion toast disappear.
        screenshot("44-wubi-frequency-settings-0.4.0")
        passed += "frequency_three_clear_dialogs_do_not_delete_other_categories_ui"
        // Old fixed-order regression scenarios keep their original expectations.
        runOnMainSync { toggle.isChecked = false; editor.setText(""); editor.requestFocus() }
        refresh()
        store.close()
    }
    private fun checkCandidateArea(shown: Boolean) {
        // Panel switches resolve pending composition asynchronously. Inspect only
        // the active window and wait for its visibility/layout update to settle.
        await {
            var correct = false
            runOnMainSync {
                val row = windows().filterIsInstance<CandidateStrip>().firstOrNull {
                    it.isAttachedToWindow && it.windowVisibility == View.VISIBLE && (it.parent as View).isShown
                }
                if (row != null) {
                    val header = row.parent as ViewGroup
                    val parent = header.parent as ViewGroup
                    val tools = header.getChildAt(1)
                    val globe = header.getChildAt(0)
                    correct = row.visibility == (if (shown) View.VISIBLE else View.GONE) &&
                        tools.visibility == (if (shown) View.GONE else View.VISIBLE) && parent.childCount == 2 && header.childCount == 3 &&
                        !parent.isLayoutRequested && parent.getChildAt(1).top == header.bottom &&
                        header.height == targetContext.dp(44) && visible(globe) &&
                        globe.contentDescription == "切换语言"
                }
            }
            correct
        }
    }
    private fun screenshot(name: String) {
        waitForIdleSync()
        SystemClock.sleep(180) // Let the next display frame reach the screenshot buffer.
        val bitmap = uiAutomation.takeScreenshot() ?: return
        val directory = File(targetContext.externalCacheDir, "qa").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun windows(): List<View> = WindowInspector.getGlobalWindowViews().flatMap { allViews(it) }
    private fun shell(command: String): String = android.os.ParcelFileDescriptor.AutoCloseInputStream(
        uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText() }
    private fun visible(view: View): Boolean = view.isShown && view.isAttachedToWindow && view.windowVisibility == View.VISIBLE
    private fun reveal(label: String) {
        runOnMainSync {
            val button = windows().filterIsInstance<Button>().first { visible(it) && it.text.toString() == label }
            button.requestRectangleOnScreen(android.graphics.Rect(0, 0, button.width, button.height), true)
        }
        waitForIdleSync()
    }
    private fun findButton(label: String): Button? {
        var found: Button? = null
        runOnMainSync { found = windows().filterIsInstance<Button>().firstOrNull { (it.text.toString() == label || it.contentDescription?.toString() == label) && visible(it) } }
        return found
    }
    // Called only from runOnMainSync; avoid nesting the instrumentation main-thread helper.
    private fun findButtonOnMain(label: String): Button = windows().filterIsInstance<Button>().first {
        visible(it) && (it.text.toString() == label || it.contentDescription?.toString() == label)
    }
    private fun findTextOnMain(label: String): TextView? = windows().filterIsInstance<TextView>().firstOrNull {
        visible(it) && it.text.toString() == label
    }
    private fun findKeyboardButton(char: Char): Button = windows().filterIsInstance<Button>().first { visible(it) && (it.tag == char.toString() || it.contentDescription == char.toString()) }
    private fun awaitKeyCase(uppercase: Boolean) {
        await {
            var correct = false
            runOnMainSync { correct = findKeyboardButton('p').text.toString() == if (uppercase) "P" else "p" }
            correct
        }
    }
    private fun findText(text: String): TextView? {
        var found: TextView? = null
        runOnMainSync { found = windows().filterIsInstance<TextView>().firstOrNull { it.text.toString() == text && visible(it) } }
        return found
    }
    private fun click(label: String, last: Boolean = false) {
        await { findButton(label) != null }
        runOnMainSync {
            val buttons = windows().filterIsInstance<Button>().filter { (it.text.toString() == label || it.contentDescription?.toString() == label) && visible(it) }
            (if (last) buttons.last() else buttons.first()).performClick()
        }
        waitForIdleSync()
    }
    private fun longPress(label: String) {
        touchButton(label, android.view.ViewConfiguration.getLongPressTimeout() + 150L)
    }
    private fun tap(label: String) { touchButton(label, 50L) }
    private fun touchButton(label: String, holdMillis: Long) {
        var previousPosition: List<Int>? = null
        var stableSince = 0L
        await {
            var laidOut = false
            runOnMainSync {
                val button = windows().filterIsInstance<Button>().firstOrNull {
                    visible(it) && (it.text.toString() == label || it.contentDescription?.toString() == label)
                }
                if (button != null && button.isLaidOut && button.width > 0 && button.height > 0) {
                    val position = IntArray(2).also { button.getLocationOnScreen(it) }.toList() + listOf(button.width, button.height)
                    if (position != previousPosition) { previousPosition = position; stableSince = SystemClock.uptimeMillis() }
                    laidOut = SystemClock.uptimeMillis() - stableSince >= 150L
                }
            }
            laidOut
        }
        val location = IntArray(2)
        var x = 0f
        var y = 0f
        runOnMainSync {
            val button = findButtonOnMain(label)
            if (holdMillis > android.view.ViewConfiguration.getLongPressTimeout()) check(button.isLongClickable)
            button.getLocationOnScreen(location)
            x = location[0] + button.width / 2f
            y = location[1] + button.height / 2f
        }
        val down = SystemClock.uptimeMillis()
        sendPointerSync(android.view.MotionEvent.obtain(down, down, android.view.MotionEvent.ACTION_DOWN, x, y, 0))
        SystemClock.sleep(holdMillis)
        sendPointerSync(android.view.MotionEvent.obtain(down, SystemClock.uptimeMillis(), android.view.MotionEvent.ACTION_UP, x, y, 0))
        waitForIdleSync()
    }
    private fun chooseLanguage(language: InputLanguage) {
        // Menu semantics are separate from the real DOWN/UP globe cases above.
        await { findButton("切换语言") != null }
        runOnMainSync { findButtonOnMain("切换语言").performLongClick() }
        await {
            var shown = false
            runOnMainSync { shown = windows().any { visible(it) && it.contentDescription == "输入语言列表" } }
            shown
        }
        click(language.label)
        await { findButton("␣ ${language.label}") != null }
    }
    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 8000
        while (!condition()) { check(SystemClock.uptimeMillis() < deadline) { "UI condition timed out; passed=${passed.joinToString()}" }; SystemClock.sleep(50) }
    }
}
