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
import com.fanson.ime.emoji.EmojiRepository
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
import java.io.File

/** Run only on a disposable emulator. No existing user clip is read or retained. */
class SmokeInstrumentation : Instrumentation() {
    private val passed = mutableListOf<String>()
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        try {
            val store = LocalStore(targetContext)
            store.put("wubi_enter_action", "commit_code")
            store.put("number_row", "true")
            store.put("roots", "false")
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
            store.close()
            val activity = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitForIdleSync()
            check(targetContext.applicationInfo.loadLabel(targetContext.packageManager).toString() == "放松输入法")
            passed += "application_name"
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
            val enterSpinner = allViews(activity.window.decorView).filterIsInstance<android.widget.Spinner>().first { it.contentDescription == "回车键功能" }
            runOnMainSync { check((0 until enterSpinner.count).map { enterSpinner.getItemAtPosition(it).toString() } == listOf("上屏五笔编码", "清除五笔编码", "上屏首个候选")) }
            screenshot("18-local-trial-settings")
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
            await { findButton("五笔86")?.isEnabled == true }
            fun refreshKeyboard() {
                runOnMainSync { manager.hideSoftInputFromWindow(editor.windowToken, 0) }
                await { findButton("退格") == null }
                runOnMainSync { manager.restartInput(editor); manager.showSoftInput(editor, android.view.inputmethod.InputMethodManager.SHOW_FORCED) }
                await { findButton("剪贴板") != null && findButton("五笔86")?.isEnabled == true }
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
            checkCandidateArea(false)
            var idleKeyboardHeight = 0
            runOnMainSync { idleKeyboardHeight = (windows().filterIsInstance<CandidateStrip>().first { it.isAttachedToWindow && (it.parent as View).isShown }.parent as View).height }
            screenshot("25-idle-toolbar")
            click("a"); await { findText("编码：a") != null }
            checkCandidateArea(true)
            runOnMainSync { check((windows().filterIsInstance<CandidateStrip>().first { visible(it) }.parent as View).height == idleKeyboardHeight) }
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
            click("五笔86"); await { findButton("abc") != null }
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
            click("abc"); await { findButton("五笔86") != null }
            runOnMainSync { editor.setText("") }
            passed += "english_header_follows_composition_and_spelling_toggle"
            checkNumberRow(true)
            "1234567890".forEach { click(it.toString()) }
            await { editor.text.toString() == "1234567890" }
            runOnMainSync { "qwertyuiop".forEach { check(!findKeyboardButton(it).isLongClickable) } }
            passed += "number_row_default_order_wubi_input_and_removed_long_press_digits"
            click("五笔86"); await { findButton("abc") != null }
            checkNumberRow(true)
            "1234567890".forEach { click(it.toString()) }
            click("z"); click("换行")
            await { editor.text.toString() == "12345678901234567890z" }
            click("abc"); await { findButton("五笔86") != null }
            runOnMainSync { editor.setText("") }
            setToggle("显示顶部数字行", false)
            checkNumberRow(false)
            LocalStore(targetContext).use { check(it.get("number_row", "true") == "false") }
            click("五笔86"); await { findButton("abc") != null }; checkNumberRow(false)
            click("abc"); await { findButton("五笔86") != null }
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
            click("五笔86"); await { findButton("abc") != null }
            runOnMainSync { check(!findKeyboardButton('q').isLongClickable) }
            click("abc"); await { findButton("五笔86") != null }
            setToggle("字母键显示字根提示", false)
            runOnMainSync { check(!findKeyboardButton('q').isLongClickable) }
            click("拼音反查（暂未开放）")
            checkCandidateArea(false)
            runOnMainSync {
                val lookup = windows().filterIsInstance<Button>().first { visible(it) && it.contentDescription == "拼音反查（暂未开放）" }
                check(lookup.text.isEmpty() && lookup.foreground != null)
                check(editor.text.toString() == "a1")
            }
            passed += "root_switch_gates_long_press_and_wubi_lookup_icon_keeps_english_z"
            runOnMainSync { editor.setText("") }
            refreshKeyboard()
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
            click("a"); await { findText("编码：a") != null }
            click("换行"); checkCandidateArea(false)
            runOnMainSync { check(editor.text.toString() == "a\n") }
            click("换行"); await { editor.text.toString() == "a\n\n" }
            passed += "return_clear_code_preserves_editor_then_newline_ui"
            setEnterAction(WubiEnterAction.FIRST_CANDIDATE)
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
            click("切换大小写"); await { findButton("ABC") != null }
            click("a"); await { editor.text.toString() == "A" }
            click("切换大小写"); awaitKeyCase(false)
            click("b"); await { editor.text.toString() == "Ab" }
            click("切换大小写"); awaitKeyCase(true)
            click("c"); await { editor.text.toString() == "AbC" }
            click("换行")
            click("ABC"); await { findButton("五笔86") != null }
            passed += "shift_upper_lower_mode_return_ui"
            click("d"); await { findText("编码：d") != null }
            click("切换大小写"); await { findButton("ABC") != null && editor.text.toString() == "AbCd" }
            click("a"); await { editor.text.toString() == "AbCdA" }
            click("换行")
            click("ABC"); await { findButton("五笔86") != null }
            passed += "shift_pending_code_as_raw_letters_ui"
            runOnMainSync { editor.setText("") }
            screenshot("01-keyboard")
            checkCandidateArea(false)
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
            click("五笔86"); await { findButton("abc") != null }
            click("123"); click("返回"); await { findButton("abc") != null }
            click("abc"); await { findButton("五笔86") != null }
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
            click("五笔86"); await { findButton("abc") != null }
            click("a"); await { editor.text.toString() == "工a" }
            passed += "latin_input_ui"
            click("换行") // Finish the current English word to restore the toolbar.
            click("abc"); await { findButton("简") != null }
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
            click("五笔86"); await { findButton("自动拼写已开启") != null }
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
            click("abc"); await { findButton("简") != null }
            passed += "english_toggle_commits_raw_and_cursor_move_ends_composition_ui"
            runOnMainSync { editor.setText("") }
            waitForIdleSync()
            click("切换大小写"); await { findButton("ABC") != null }
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
            await { findButton("简") != null && findButton("五笔86") != null }
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
                    val parent = row.parent as ViewGroup
                    val index = parent.indexOfChild(row)
                    val tools = parent.getChildAt(index - 1)
                    val activeHeader = if (shown) row else tools
                    correct = row.visibility == (if (shown) View.VISIBLE else View.GONE) &&
                        tools.visibility == (if (shown) View.GONE else View.VISIBLE) && parent.childCount == 3 &&
                        !parent.isLayoutRequested && parent.getChildAt(index + 1).top == activeHeader.bottom &&
                        activeHeader.height == targetContext.dp(44)
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
    private fun findKeyboardButton(char: Char): Button = windows().filterIsInstance<Button>().first { visible(it) && it.contentDescription == char.toString() }
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
    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 8000
        while (!condition()) { check(SystemClock.uptimeMillis() < deadline) { "UI condition timed out; passed=${passed.joinToString()}" }; SystemClock.sleep(50) }
    }
}
