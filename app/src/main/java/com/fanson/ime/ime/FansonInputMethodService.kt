package com.fanson.ime.ime

import android.content.Intent
import android.content.res.Configuration
import android.icu.text.BreakIterator
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.*
import com.fanson.ime.R
import com.fanson.ime.clipboard.ClipboardController
import com.fanson.ime.clipboard.ClipboardSnapshot
import com.fanson.ime.data.LocalStore
import com.fanson.ime.data.WubiRepository
import com.fanson.ime.data.PronunciationRepository
import com.fanson.ime.emoji.Emoji
import com.fanson.ime.emoji.EmojiRepository
import com.fanson.ime.engine.Candidate
import com.fanson.ime.engine.EngineResult
import com.fanson.ime.engine.WubiEngine
import com.fanson.ime.engine.WubiEnterAction
import com.fanson.ime.engine.InputEngine
import com.fanson.ime.engine.EnglishEngine
import com.fanson.ime.engine.EnglishLexicon
import com.fanson.ime.engine.TraditionalConverter
import com.fanson.ime.engine.InputLanguage
import com.fanson.ime.engine.KoreanEngine
import com.fanson.ime.keyboard.Palette
import com.fanson.ime.keyboard.CandidateStrip
import com.fanson.ime.keyboard.candidateButton
import com.fanson.ime.keyboard.EmojiCategoryStrip
import com.fanson.ime.keyboard.SymbolPanel
import com.fanson.ime.keyboard.WubiRootCard
import com.fanson.ime.keyboard.LanguagePopup
import com.fanson.ime.keyboard.Ui
import com.fanson.ime.keyboard.dp
import com.fanson.ime.settings.MainActivity
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

class FansonInputMethodService : InputMethodService() {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val session = AtomicLong()
    private var destroyed = false
    private var ready = false
    private var loadFailed = false
    private var engine: WubiEngine? = null // worker-confined
    private var englishEngine: EnglishEngine? = null // loaded on worker, then main-thread confined
    private val koreanEngine = KoreanEngine() // main-thread confined
    private var repository: WubiRepository? = null
    private var pronunciation: PronunciationRepository? = null
    private var emoji: EmojiRepository? = null
    private lateinit var store: LocalStore
    private lateinit var clipboard: ClipboardController
    private lateinit var ui: Ui
    private var root: LinearLayout? = null
    private var candidateRow: CandidateStrip? = null
    private var toolbar: LinearLayout? = null
    private var body: LinearLayout? = null
    private var candidateSnapshot: List<Candidate> = emptyList()
    private var currentCode = ""
    private var showingReadings = false
    private var pronunciationKey: Button? = null
    private var language = InputLanguage.WUBI
    private val chinese get() = language == InputLanguage.WUBI
    private val english get() = language == InputLanguage.ENGLISH
    private val korean get() = language == InputLanguage.KOREAN
    private var koreanShift = false
    private var languagePopup: LanguagePopup? = null
    private var globe: Button? = null
    private var uppercase = false
    private var traditional = false
    private var spelling = true
    private var privateField = false
    private var password = false
    private var keyHeight = 48
    private var haptics = false
    private var showRootHints = false
    private var showNumberRow = true
    private var rootCard: WubiRootCard? = null
    private var wubiEnterAction = WubiEnterAction.COMMIT_CODE
    private var panel = "keyboard"
    private var expanded = false
    private var emojiSearching = false
    private var emojiQuery = ""
    private var emojiCategory = "popular"
    private var emojiCategoryStrip: EmojiCategoryStrip? = null
    private var emojiCategoryScrollX = 0
    private var emojiPage = 0
    private var glyphCache = mutableMapOf<String, Boolean>()
    private var clipboardSnapshot: ClipboardSnapshot? = null
    private var clipboardRevealed = false
    private var clipboardExpanded = false
    private var clipPreview: TextView? = null
    private var lastVibration = 0L
    private var repeatAction: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        store = LocalStore(this)
        clipboard = ClipboardController(this)
        // Stay attached for the lifetime of the default IME service so copies
        // made while the keyboard/panel is hidden can enter the requested history.
        clipboard.observe {
            val latest = captureClipboard()
            if (panel == "clipboard" && isInputViewShown) {
                refreshClipboard(latest); renderCandidates(); renderBody()
            }
        }
        captureClipboard()
        worker.execute {
            try {
                repository = WubiRepository(this, store)
                val converter = assets.open("traditional.tsv").bufferedReader().use { TraditionalConverter.read(it.lineSequence()) }
                pronunciation = PronunciationRepository(this)
                engine = WubiEngine(repository!!, converter, pronunciation)
                val words = assets.open("english.txt").bufferedReader().use { it.readLines() }
                englishEngine = EnglishEngine(EnglishLexicon(words))
                emoji = EmojiRepository(this)
                main.post { if (!destroyed) { ready = true; render() } }
            } catch (_: Exception) {
                main.post { if (!destroyed) { loadFailed = true; render() } }
            }
        }
    }

    override fun onCreateInputView(): View {
        ui = Ui(this, Palette.of(this, store.get("theme", "system")))
        return ui.column().also { root = it; render() }
    }

    override fun onEvaluateFullscreenMode() = false

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        invalidateSession()
        privateField = EditorPolicy.privateField(attribute)
        password = EditorPolicy.password(attribute.inputType)
        val preferred = InputLanguage.fromPreference(store.get("input_language", "wubi86"))
        language = EditorPolicy.initialLanguage(attribute.inputType, preferred)
        panel = if (EditorPolicy.numeric(attribute.inputType)) "numbers" else "keyboard"
        uppercase = false; koreanShift = false
        readSettings()
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        readSettings()
        render()
    }

    private fun readSettings() {
        keyHeight = (store.get("key_height", "48").toIntOrNull() ?: 48).coerceIn(40, 64)
        haptics = store.get("haptic", "false").toBoolean()
        showRootHints = store.get("roots", "false").toBoolean()
        showNumberRow = store.get("number_row", "true").toBoolean()
        rootCard?.dismiss(); rootCard = null
        languagePopup?.dismiss(); languagePopup = null
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) keyHeight = minOf(keyHeight, 40)
        ui = Ui(this, Palette.of(this, store.get("theme", "system")))
        val auto = store.get("auto_unique", "true").toBoolean()
        val fifth = store.get("fifth_commit", "true").toBoolean()
        val enterAction = WubiEnterAction.fromPreference(store.get("wubi_enter_action", "commit_code"))
        wubiEnterAction = enterAction
        worker.execute { engine?.let { it.autoCommitUnique = auto; it.commitOnFifth = fifth; it.enterAction = enterAction } }
    }

    private fun invalidateSession() {
        rootCard?.dismiss()
        languagePopup?.dismiss()
        session.incrementAndGet()
        releaseClip()
        cancelRepeat()
        currentCode = ""; candidateSnapshot = emptyList(); expanded = false
        showingReadings = false
        emojiQuery = ""; emojiSearching = false; emojiPage = 0
        emojiCategoryStrip = null; emojiCategoryScrollX = 0
        englishEngine?.reset()
        koreanEngine.reset(); koreanShift = false
        if (!destroyed && !worker.isShutdown) worker.execute { engine?.reset() }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        currentInputConnection?.finishComposingText()
        invalidateSession(); panel = "keyboard"
        render()
        super.onFinishInputView(finishingInput)
    }
    override fun onFinishInput() {
        invalidateSession(); privateField = false; password = false
        super.onFinishInput()
    }

    /** SQL-backed Wubi runs on the worker. In-memory English mutates with its
     * composing edits on main, so cursor reports cannot split a queued word. */
    private fun inputTask(mode: InputLanguage, token: Long, operation: (InputEngine) -> Unit) {
        val task = Runnable {
            if (destroyed || token != session.get()) return@Runnable
            val e: InputEngine = when (mode) {
                InputLanguage.WUBI -> engine
                InputLanguage.ENGLISH -> englishEngine
                InputLanguage.KOREAN -> koreanEngine
            } ?: return@Runnable
            operation(e)
        }
        if (mode == InputLanguage.WUBI) worker.execute(task) else task.run()
    }

    private fun publishInput(mode: InputLanguage, token: Long, action: () -> Unit) {
        val task = Runnable { if (!destroyed && token == session.get()) action() }
        if (mode == InputLanguage.WUBI) main.post(task) else task.run()
    }

    private fun enqueue(operation: (InputEngine) -> EngineResult, after: (() -> Unit)? = null) {
        if (destroyed) return
        if (!ready) { hint(if (loadFailed) "离线数据加载失败，请重新打开输入法" else "正在准备离线码表"); return }
        val token = session.get()
        val search = emojiSearching
        val mode = language
        val useTraditional = traditional && !search
        val useSpelling = spelling && !password
        inputTask(mode, token) { e ->
            if (e is WubiEngine) e.traditionalMode = useTraditional
            if (e is EnglishEngine) e.enabled = useSpelling
            val result = operation(e)
            val code = e.code
            val candidates = e.candidates.toList()
            val readings = (e as? WubiEngine)?.showingReadings == true
            publishInput(mode, token) {
                // A syllable boundary commits its prefix and composes the next
                // syllable together. Intermediate cursor callbacks must not reset it.
                editorBatch {
                    result.commits.forEach { if (search) emojiQuery += it else commitText(it) }
                    result.composing?.let { currentInputConnection?.setComposingText(it, 1) }
                    currentCode = code; candidateSnapshot = candidates; expanded = false
                    showingReadings = readings
                }
                renderCandidates()
                result.hint?.let { hint(it) }
                if (search && result.commits.isNotEmpty()) { emojiPage = 0; renderBody() }
                if (code.isEmpty()) after?.invoke()
            }
        }
    }

    private fun resolveThen(action: () -> Unit) { enqueue({ it.resolve() }, action) }
    private fun editorBatch(action: () -> Unit) {
        val connection = currentInputConnection
        connection?.beginBatchEdit()
        try { action() } finally { connection?.endBatchEdit() }
    }
    private fun commitText(text: String): Boolean {
        val connection = currentInputConnection ?: return false
        connection.beginBatchEdit()
        return try { connection.commitText(text, 1) } finally { connection.endBatchEdit() }
    }
    private fun letter(char: Char) {
        rootCard?.dismiss()
        languagePopup?.dismiss()
        vibrate()
        val resetShift = korean && koreanShift
        val typed = if ((english && uppercase) || resetShift) char.uppercaseChar() else char
        enqueue({ it.letter(typed) })
        if (resetShift) { koreanShift = false; renderBody() }
    }
    private fun punct(text: String) { rootCard?.dismiss(); languagePopup?.dismiss(); vibrate(); enqueue({ it.punctuation(text) }) }
    private fun space() { rootCard?.dismiss(); languagePopup?.dismiss(); vibrate(); enqueue({ it.space() }) }
    private fun deleteKey() {
        rootCard?.dismiss()
        languagePopup?.dismiss()
        vibrate()
        if (destroyed || !ready) return
        val token = session.get()
        val search = emojiSearching
        val mode = language
        inputTask(mode, token) { e ->
            val removedCode = e.backspace()
            val code = e.code; val candidates = e.candidates.toList()
            val readings = (e as? WubiEngine)?.showingReadings == true
            publishInput(mode, token) {
                editorBatch {
                    if (!removedCode) {
                        if (search) { emojiQuery = dropLastCluster(emojiQuery); emojiPage = 0; renderBody() }
                        else deleteEditorCluster()
                    }
                    else if (mode != InputLanguage.WUBI) {
                        currentInputConnection?.setComposingText(code, 1)
                        if (code.isEmpty()) currentInputConnection?.finishComposingText()
                    }
                    currentCode = code; candidateSnapshot = candidates; showingReadings = readings
                }
                expanded = false; renderCandidates()
            }
        }
    }

    private fun dropLastCluster(text: String): String {
        if (text.isEmpty()) return text
        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
        iterator.setText(text)
        iterator.last()
        val start = iterator.previous()
        return if (start == BreakIterator.DONE) "" else text.substring(0, start)
    }

    private fun deleteEditorCluster() {
        val ic = currentInputConnection ?: return
        if (!ic.getSelectedText(0).isNullOrEmpty()) { ic.commitText("", 1); return }
        val before = ic.getTextBeforeCursor(512, 0)?.toString().orEmpty()
        if (before.isEmpty()) {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
            return
        }
        // ICU handles grapheme clusters; the pinned table also handles newer emoji
        // sequences that older Android ICU builds may not recognise.
        val tableMatch = emoji?.all?.asSequence()?.map { it.text }?.filter { before.endsWith(it) }?.maxByOrNull { it.length }
        val removedByIcu = before.length - dropLastCluster(before).length
        val count = maxOf(removedByIcu, tableMatch?.length ?: 0)
        ic.deleteSurroundingText(count, 0)
    }

    private fun enter() {
        rootCard?.dismiss()
        languagePopup?.dismiss()
        if (destroyed || !ready) return
        vibrate()
        val token = session.get()
        val search = emojiSearching
        val mode = language
        inputTask(mode, token) { e ->
            val hadCode = e.code.isNotEmpty()
            val result = e.enter()
            val code = e.code
            val candidates = e.candidates.toList()
            val readings = (e as? WubiEngine)?.showingReadings == true
            publishInput(mode, token) {
                result.commits.forEach { if (search) emojiQuery += it else commitText(it) }
                currentCode = code; candidateSnapshot = candidates
                showingReadings = readings
                val wasExpanded = expanded
                expanded = false; renderCandidates()
                result.hint?.let { hint(it) }
                if (wasExpanded) renderBody()
                if (search) { emojiPage = 0; renderBody() }
                else if (mode == InputLanguage.KOREAN || !hadCode) {
                    val info = currentInputEditorInfo
                    val action = (info?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
                    val noAction = (info?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
                    if (!noAction && action !in setOf(EditorInfo.IME_ACTION_NONE, EditorInfo.IME_ACTION_UNSPECIFIED)) currentInputConnection?.performEditorAction(action)
                    else commitText("\n")
                }
            }
        }
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (!chinese && currentCode.isNotEmpty() && (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
            // Editor callbacks may arrive after newer edits have composed another
            // word. Ignore an old report. Request zero surrounding characters.
            val cursor = currentInputConnection?.getSurroundingText(0, 0, 0)
            if (cursor != null && cursor.offset >= 0 && (cursor.offset + cursor.selectionStart != newSelStart ||
                    cursor.offset + cursor.selectionEnd != newSelEnd)) return
            // Cursor/selection changes finish the current word; never replace text elsewhere.
            currentInputConnection?.finishComposingText()
            session.incrementAndGet()
            currentCode = ""; candidateSnapshot = emptyList(); expanded = false
            englishEngine?.reset()
            koreanEngine.reset()
            if (!destroyed) { renderCandidates(); renderBody() }
        }
    }

    private fun render() {
        if (destroyed) return
        rootCard?.dismiss(); rootCard = null
        languagePopup?.dismiss(); languagePopup = null
        val container = root ?: return
        if (!::ui.isInitialized) return
        rememberEmojiCategoryPosition()
        cancelRepeat()
        container.removeAllViews(); container.setBackgroundColor(ui.palette.background)
        container.setPadding(dp(3), dp(4), dp(3), dp(4))
        val header = ui.row().apply { contentDescription = "键盘顶部" }
        globe = ui.iconButton(R.drawable.ic_globe, "切换语言") { showLanguages() }.apply {
            isEnabled = ready
            stateDescription = language.label + if (privateField) "，隐私输入" else ""
        }.also { header.addView(it, LinearLayout.LayoutParams(dp(44), dp(40)).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }) }
        val tools = ui.row().apply { contentDescription = "功能按钮栏" }
        toolbar = tools
        fun icon(icon: Int, description: String, action: () -> Unit) {
            ui.addWeighted(tools, ui.iconButton(icon, description, action = action), height = 40)
        }
        icon(R.drawable.ic_clipboard, "剪贴板") { switchPanel("clipboard") }
        icon(R.drawable.ic_symbols, "符号") { switchPanel("symbols") }
        icon(R.drawable.ic_emoji, "表情") { if (password) hint("密码框不显示 emoji 推荐") else switchPanel("emoji") }
        icon(R.drawable.ic_settings, "设置") {
            resolveThen { requestHideSelf(0); startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        icon(R.drawable.ic_hide, "收起键盘") { requestHideSelf(0) }
        header.addView(tools, LinearLayout.LayoutParams(0, dp(44), 1f))
        candidateRow = CandidateStrip(ui, ::select, { setCandidatesExpanded(true) }, { setCandidatesExpanded(false) }) { overflow ->
            if (!overflow && expanded) setCandidatesExpanded(false)
        }
        header.addView(candidateRow, LinearLayout.LayoutParams(0, dp(44), 1f))
        container.addView(header, LinearLayout.LayoutParams(-1, dp(44)))
        body = ui.column()
        container.addView(body)
        renderCandidates(); renderBody()
    }

    private fun renderCandidates() {
        val inputPanel = panel == "keyboard" || (panel == "emoji" && emojiSearching)
        val showCandidates = inputPanel && currentCode.isNotEmpty() && !password && (chinese || (english && spelling))
        toolbar?.visibility = if (showCandidates) View.GONE else View.VISIBLE
        candidateRow?.visibility = if (showCandidates) View.VISIBLE else View.GONE
        val row = candidateRow ?: return
        val composition = if (showCandidates) "${if (!chinese) "英文" else if (emojiSearching) "搜索编码" else "编码"}：$currentCode" else ""
        row.submit(candidateSnapshot, expanded, if (chinese) wubiEnterAction.noMatchHint else "无匹配，回车提交字母", composition, chinese && showingReadings)
        updatePronunciationKey()
    }

    private fun updatePronunciationKey() {
        pronunciationKey?.let { key ->
            val active = chinese && showingReadings
            key.stateDescription = if (active) "读音已显示，再按恢复五笔提示" else "空闲输入 z，有候选时查读音"
            if (key.isSelected == active) return@let
            key.isSelected = active
            key.background = ui.button("", active) {}.background
            key.foreground?.setTint(if (active) ui.palette.accentInk else ui.palette.ink)
        }
    }

    private fun setCandidatesExpanded(value: Boolean) {
        if (destroyed || expanded == value) return
        expanded = value
        renderCandidates(); renderBody()
    }

    private fun select(candidate: Candidate) {
        vibrate()
        enqueue({ e ->
            val index = e.candidates.indexOfFirst { it.text == candidate.text && it.code == candidate.code }
            if (index < 0) EngineResult(hint = "候选已更新，请重新选择") else e.select(index)
        }, after = { if (body != null) renderBody() })
    }

    private fun renderBody() {
        rootCard?.dismiss()
        cancelRepeat()
        rememberEmojiCategoryPosition()
        val container = body ?: return
        container.removeAllViews()
        if (expanded && candidateSnapshot.isNotEmpty()) { renderExpanded(container); return }
        when (panel) {
            "clipboard" -> renderClipboard(container)
            "emoji" -> renderEmoji(container)
            "numbers" -> renderNumbers(container)
            "symbols" -> renderSymbols(container)
            else -> renderKeyboard(container)
        }
    }

    private fun showLanguages() {
        val anchor = globe ?: return
        rootCard?.dismiss()
        val popup = languagePopup ?: LanguagePopup(ui).also { languagePopup = it }
        if (popup.isShowing) popup.dismiss() else popup.show(anchor, language, ::chooseLanguage)
    }

    private fun chooseLanguage(next: InputLanguage) {
        if (next == language) return
        resolveThen {
            invalidateSession()
            language = next; uppercase = false
            store.put("input_language", next.value)
            leaveClipboard(); panel = "keyboard"; render()
        }
    }
    private fun shift() {
        vibrate()
        languagePopup?.dismiss()
        if (korean) {
            koreanShift = !koreanShift; renderBody()
        } else if (chinese) {
            enqueue({ if (it is WubiEngine) it.enter(WubiEnterAction.COMMIT_CODE) else it.enter() }, after = {
                language = InputLanguage.ENGLISH; uppercase = true
                store.put("input_language", language.value); render()
            })
        } else {
            uppercase = !uppercase
            render()
        }
    }
    private fun switchPanel(next: String) {
        resolveThen {
            leaveClipboard()
            emojiSearching = false; emojiQuery = ""; emojiPage = 0; expanded = false
            panel = if (panel == next) "keyboard" else next
            emojiCategoryStrip = null; emojiCategoryScrollX = 0
            if (panel == "clipboard") {
                refreshClipboard()
            }
            render()
        }
    }
    private fun leaveClipboard() { releaseClip() }
    private fun releaseClip() {
        clipPreview?.text = ""; clipPreview = null
        clipboardSnapshot = null; clipboardRevealed = false; clipboardExpanded = false
    }

    private fun renderKeyboard(container: LinearLayout) {
        pronunciationKey = null
        val rootHints = mapOf('q' to "金", 'w' to "人", 'e' to "月", 'r' to "白", 't' to "禾", 'y' to "言", 'u' to "立", 'i' to "水", 'o' to "火", 'p' to "之", 'a' to "工", 's' to "木", 'd' to "大", 'f' to "土", 'g' to "王", 'h' to "目", 'j' to "日", 'k' to "口", 'l' to "田", 'x' to "纟", 'c' to "又", 'v' to "女", 'b' to "子", 'n' to "已", 'm' to "山")
        val showRoots = chinese && showRootHints && !password
        if (showNumberRow) {
            val numberRow = ui.row().apply { contentDescription = "数字行" }
            "1234567890".forEach { digit ->
                ui.addWeighted(numberRow, ui.button(digit.toString()) { punct(digit.toString()) }.apply { textSize = 18f }, height = keyHeight)
            }
            container.addView(numberRow)
        }
        listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").forEachIndexed { index, chars ->
            val row = ui.row()
            if (index == 1) row.setPadding(dp(14), 0, dp(14), 0)
            if (index == 2) ui.addWeighted(row, ui.iconButton(
                if ((uppercase && english) || (korean && koreanShift)) R.drawable.ic_caps else R.drawable.ic_shift,
                if (korean) "韩语上档" else "切换大小写", accent = korean && koreanShift, action = { shift() }
            ).apply { isSelected = korean && koreanShift }, 1.25f, keyHeight)
            chars.forEach { char ->
                val label = if (korean) KoreanEngine.keyLabel(if (koreanShift) char.uppercaseChar() else char).toString()
                    else if (chinese || uppercase) char.uppercase() else char.toString()
                val key = if (chinese && char == 'z') ui.iconButton(R.drawable.ic_pronunciation_lookup, "Z / 查读音", showingReadings) {
                    letter('z')
                }.also { pronunciationKey = it }
                else ui.button(label + if (showRoots && rootHints[char] != null) "\n${rootHints[char]}" else "") { letter(char) }
                key.textSize = if (showRoots) 14f else 20f
                key.tag = char.toString()
                if (!chinese || char != 'z') key.contentDescription = if (korean) label else char.toString()
                if (showRoots && char in 'a'..'y') key.setOnLongClickListener {
                    vibrate()
                    (rootCard ?: WubiRootCard(ui).also { rootCard = it }).show(key, char)
                    true
                }
                else key.isLongClickable = false
                ui.addWeighted(row, key, height = keyHeight)
            }
            if (index == 2) ui.addWeighted(row, deleteButton(), 1.25f, keyHeight)
            container.addView(row)
        }
        val bottom = ui.row()
        ui.addWeighted(bottom, ui.button("123") { switchPanel("numbers") }, 1.1f, keyHeight)
        val languageKey = if (korean) ui.button("한/漢") {}.apply {
            isEnabled = false; alpha = .55f
            contentDescription = "한/漢（暂未开放）"
        } else if (chinese) ui.button(if (traditional && !emojiSearching) "繁" else "简") {
            if (emojiSearching) hint("表情搜索使用简体关键词")
            else {
                traditional = !traditional
                enqueue({ EngineResult() })
                renderBody()
            }
        }.apply { contentDescription = if (traditional && !emojiSearching) "繁体输出，点击切换简体" else "简体输出，点击切换繁体" }
        else ui.iconButton(
            if (spelling && !password) R.drawable.ic_spelling_on else R.drawable.ic_spelling_off,
            if (spelling && !password) "自动拼写已开启" else "自动拼写已关闭", spelling && !password
        ) {
            if (password) hint("密码框不启用自动拼写")
            else resolveThen { spelling = !spelling; renderBody(); renderCandidates() }
        }.apply { isSelected = spelling && !password; isEnabled = !password }
        ui.addWeighted(bottom, languageKey, 1.2f, keyHeight)
        ui.addWeighted(bottom, ui.button(if (chinese) "，" else ",") { punct(if (chinese) "，" else ",") }, .8f, keyHeight)
        val space = spaceButton()
        space.setOnLongClickListener { showCursorControls(); true }
        ui.addWeighted(bottom, space, 3.3f, keyHeight)
        ui.addWeighted(bottom, ui.button(if (chinese) "。" else ".") { punct(if (chinese) "。" else ".") }, .8f, keyHeight)
        ui.addWeighted(bottom, enterButton(), 1.4f, keyHeight)
        container.addView(bottom)
        updatePronunciationKey()
    }

    private fun enterButton(): Button = if (enterLabel() == "换行") {
        ui.iconButton(R.drawable.ic_enter, "换行", true) { enter() }
    } else ui.button(enterLabel(), true) { enter() }.apply { textSize = 14f }

    private fun enterLabel(): String {
        if (emojiSearching) return "搜索"
        val info = currentInputEditorInfo ?: return "换行"
        if (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return "换行"
        return when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_SEND -> "发送"
            EditorInfo.IME_ACTION_SEARCH -> "搜索"
            EditorInfo.IME_ACTION_DONE -> "完成"
            EditorInfo.IME_ACTION_NEXT -> "下一项"
            EditorInfo.IME_ACTION_GO -> "前往"
            else -> "换行"
        }
    }

    // Return false from the observer so Button keeps its normal, accessible
    // performClick path. The observer only adds repeat-on-hold behaviour.
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun deleteButton(): Button = ui.button("⌫") { deleteKey() }.apply {
        contentDescription = "退格"
        setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    cancelRepeat()
                    repeatAction = object : Runnable {
                        override fun run() { deleteKey(); main.postDelayed(this, 85) }
                    }.also { main.postDelayed(it, 420) }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> cancelRepeat()
            }
            false
        }
    }
    private fun cancelRepeat() { repeatAction?.let { main.removeCallbacks(it) }; repeatAction = null }

    private fun renderNumbers(container: LinearLayout) {
        listOf(listOf("1", "2", "3", "+"), listOf("4", "5", "6", "-"), listOf("7", "8", "9", "*"), listOf(".", "0", "@", "/")).forEach { values ->
            val row = ui.row(); values.forEach { value -> ui.addWeighted(row, ui.button(value) { punct(value) }, height = keyHeight) }; container.addView(row)
        }
        val row = ui.row()
        ui.addWeighted(row, ui.button("返回") { switchPanel("numbers") }, height = 44)
        ui.addWeighted(row, ui.button("_") { punct("_") }, height = 44)
        ui.addWeighted(row, deleteButton(), height = 44)
        ui.addWeighted(row, enterButton(), height = 44)
        container.addView(row)
    }
    private fun renderSymbols(container: LinearLayout) {
        container.addView(SymbolPanel(ui, keyHeight, ::punct), LinearLayout.LayoutParams(-1, dp((keyHeight + 4) * 4)))
        val row = ui.row()
        ui.addWeighted(row, ui.button("返回键盘") { switchPanel("symbols") }, height = 44)
        ui.addWeighted(row, deleteButton(), height = 44)
        ui.addWeighted(row, spaceButton(), height = 44)
        ui.addWeighted(row, enterButton(), height = 44)
        container.addView(row)
    }
    private fun renderExpanded(container: LinearLayout) {
        val scroll = ScrollView(this)
        val grid = GridLayout(this).apply { columnCount = if (chinese && showingReadings) 2 else 3 }
        candidateSnapshot.forEach { candidate ->
            val button = candidateButton(ui, candidate, chinese && showingReadings) { select(candidate) }.apply {
                if (!showingReadings) { textSize = 16f; setPadding(dp(3), 0, dp(3), 0) }
            }
            val cell: View = if (chinese && showingReadings) HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false; isFillViewport = true
                addView(button, ViewGroup.LayoutParams(-2, -1))
            } else button
            grid.addView(cell, GridLayout.LayoutParams().apply {
                width = 0; height = dp(52); columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); setMargins(dp(2), dp(2), dp(2), dp(2))
            })
        }
        scroll.addView(grid); container.addView(scroll, LinearLayout.LayoutParams(-1, dp(keyHeight * 4)))
        container.addView(ui.button("返回键盘") { setCandidatesExpanded(false) }, LinearLayout.LayoutParams(-1, dp(44)))
    }

    private fun rememberEmojiCategoryPosition() {
        emojiCategoryStrip?.let { emojiCategoryScrollX = it.retainedScrollX }
        emojiCategoryStrip = null
    }

    private fun renderEmoji(container: LinearLayout) {
        val repo = emoji ?: return
        val header = ui.row()
        ui.addWeighted(header, ui.button("‹ 键盘") { switchPanel("emoji") }.apply { textSize = 12f }, 1f, 40)
        if (chinese) ui.addWeighted(header, ui.button(if (emojiSearching) "关键词：${emojiQuery.ifEmpty { "输入中文" }}" else "五笔搜索 emoji") {
            resolveThen { emojiSearching = !emojiSearching; renderBody(); renderCandidates() }
        }.apply { textSize = 13f }, 3f, 40)
        if (emojiQuery.isNotEmpty()) ui.addWeighted(header, ui.button("清空") { resolveThen { emojiQuery = ""; emojiPage = 0; renderBody() } }.apply { textSize = 12f }, 1f, 40)
        container.addView(header)
        if (!emojiSearching) {
            val categoryScroll = EmojiCategoryStrip(this, emojiCategoryScrollX) { source, x ->
                if (emojiCategoryStrip === source) emojiCategoryScrollX = x
            }
            emojiCategoryStrip = categoryScroll
            val tabs = ui.row()
            repo.categories.forEach { (label, value) ->
                tabs.addView(ui.button(label, emojiCategory == value) { emojiCategory = value; emojiPage = 0; emojiQuery = ""; renderBody() }.apply { textSize = 12f }, LinearLayout.LayoutParams(dp(52), dp(36)))
            }
            categoryScroll.addView(tabs); container.addView(categoryScroll)
        }
        val paint = android.text.TextPaint().apply { textSize = 30f }
        val commonPage = !emojiSearching && emojiCategory == "popular" && emojiQuery.isBlank()
        val filtered = if (emojiSearching && emojiQuery.isBlank()) emptyList() else repo.filter(emojiCategory, emojiQuery, store.emojiUsage()) {
            glyphCache.getOrPut(it.text) { paint.hasGlyph(it.text) }
        }
        val pageSize = if (emojiSearching) 12 else 30
        val pages = if (commonPage) 1 else maxOf(1, (filtered.size + pageSize - 1) / pageSize)
        emojiPage = emojiPage.coerceIn(0, pages - 1)
        val grid = GridLayout(this).apply { columnCount = 6 }
        filtered.drop(emojiPage * pageSize).take(pageSize).forEach { item ->
            val key = ui.button(item.text) { insertEmoji(item) }.apply { textSize = 27f; contentDescription = item.name }
            key.isLongClickable = false
            grid.addView(key, GridLayout.LayoutParams().apply {
                width = 0; height = dp(if (emojiSearching) 42 else 44)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); setMargins(dp(2), dp(2), dp(2), dp(2))
            })
        }
        if (filtered.isEmpty()) container.addView(ui.text(if (emojiSearching && emojiQuery.isBlank()) "输入关键词查找表情" else "没有匹配或设备暂不支持这些表情", 13f, ui.palette.muted))
        container.addView(grid)
        if (!commonPage && pages > 1) {
            val navigation = ui.row()
            ui.addWeighted(navigation, ui.button("‹") { emojiPage = maxOf(0, emojiPage - 1); renderBody() }, height = 32)
            ui.addWeighted(navigation, ui.text("${emojiPage + 1} / $pages", 12f).apply { gravity = Gravity.CENTER }, 2f, 32)
            ui.addWeighted(navigation, ui.button("›") { emojiPage = minOf(pages - 1, emojiPage + 1); renderBody() }, height = 32)
            container.addView(navigation)
        }
        if (emojiSearching) renderKeyboard(container)
        else {
            val row = ui.row()
            ui.addWeighted(row, ui.text(if (commonPage) "常用按本地使用次数排序" else "点击输入表情", 12f, ui.palette.muted), 3f, 40)
            ui.addWeighted(row, deleteButton(), height = 40)
            container.addView(row)
        }
    }

    private fun insertEmoji(item: Emoji) {
        // Search preedit belongs to the private panel; only the explicitly chosen
        // emoji is inserted into the host editor.
        val token = session.get()
        worker.execute {
            engine?.reset()
            main.post {
                if (token != session.get() || destroyed) return@post
                if (commitText(item.text) && !privateField) store.recordEmojiUsage(item.text)
                // Keep this grid instance/order stable until the user navigates.
                currentCode = ""; candidateSnapshot = emptyList(); renderCandidates()
            }
        }
    }

    private fun spaceButton() = ui.button("␣ ${language.label}") { space() }.apply {
        contentDescription = "空格"; stateDescription = language.label
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && languagePopup?.isShowing == true) {
            languagePopup?.dismiss(); return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun captureClipboard(): ClipboardSnapshot? {
        if (getSystemService(android.app.KeyguardManager::class.java).isDeviceLocked) return null
        val latest = clipboard.read()
        if (!privateField && latest.kind == ClipboardSnapshot.Kind.TEXT) {
            latest.text?.let { store.recordClipboard(it, latest.sensitive) }
        }
        return latest
    }
    private fun refreshClipboard(latest: ClipboardSnapshot? = clipboard.read()) {
        if (latest != clipboardSnapshot) { clipboardRevealed = false; clipboardExpanded = false }
        clipboardSnapshot = latest
    }
    private fun renderClipboard(container: LinearLayout) {
        val snapshot = clipboardSnapshot ?: clipboard.read().also { clipboardSnapshot = it }
        val row = ui.row()
        ui.addWeighted(row, ui.button("‹ 返回键盘") { switchPanel("clipboard") }.apply { textSize = 13f }, 2f, 42)
        ui.addWeighted(row, ui.button("一键清空", true) {
            val success = clipboard.clear()
            store.clearClipboardHistory()
            releaseClip(); refreshClipboard(); renderBody()
            hint(if (success) "当前剪贴板与历史已清空" else "历史已清空；系统当前剪贴板清空失败")
        }.apply { textSize = 13f }, 2f, 42)
        container.addView(row)
        val hidden = (snapshot.sensitive || privateField) && !clipboardRevealed
        val preview = when (snapshot.kind) {
            ClipboardSnapshot.Kind.TEXT -> if (hidden) "••••••\n敏感内容已遮挡" else if (clipboardExpanded) snapshot.text.orEmpty().take(100000) else snapshot.text.orEmpty().take(1500)
            ClipboardSnapshot.Kind.EMPTY -> "剪贴板为空"
            ClipboardSnapshot.Kind.NON_TEXT -> "当前为图片或文件，首版支持查看与粘贴文本；仍可一键清空。"
            ClipboardSnapshot.Kind.UNAVAILABLE -> "当前无法访问剪贴板，请确认已选中放松输入法。"
        }
        val scroll = ScrollView(this).apply { background = ui.round(ui.palette.surface, border = true) }
        val contents = ui.column()
        contents.addView(ui.text("当前内容", 12f, ui.palette.muted))
        clipPreview?.text = ""
        clipPreview = ui.text(preview, 15f).apply { setPadding(dp(12), dp(12), dp(12), dp(12)); isSaveEnabled = false }
        contents.addView(clipPreview)
        if (hidden) contents.addView(ui.button("显示内容") { clipboardRevealed = true; renderBody() }, LinearLayout.LayoutParams(-1, dp(36)))
        else if (snapshot.kind == ClipboardSnapshot.Kind.TEXT && snapshot.text.orEmpty().length > 1500) {
            contents.addView(ui.button(if (clipboardExpanded) "收起长文本" else "展开长文本（最多显示 10 万字符，粘贴使用全文）") { clipboardExpanded = !clipboardExpanded; renderBody() }.apply { textSize = 12f }, LinearLayout.LayoutParams(-1, dp(36)))
        }
        val paste = ui.button("粘贴到输入框", true) {
            val latest = clipboard.read()
            if (latest != clipboardSnapshot) { clipboardSnapshot = latest; clipboardRevealed = false; renderBody(); hint("剪贴板已更新，请重新选择粘贴") }
            else latest.text?.let { value -> resolveThen { commitText(value) } }
        }.apply { isEnabled = snapshot.kind == ClipboardSnapshot.Kind.TEXT }
        contents.addView(paste, LinearLayout.LayoutParams(-1, dp(44)))
        val history = store.clipboardHistory()
        contents.addView(ui.text("最近 ${history.size} / 10 项（含当前已记录文本）", 12f, ui.palette.muted))
        val historyHidden = privateField && !clipboardRevealed
        if (historyHidden && history.isNotEmpty()) {
            contents.addView(ui.text("历史内容已遮挡", 14f))
            if (!hidden) contents.addView(ui.button("显示历史") { clipboardRevealed = true; renderBody() }, LinearLayout.LayoutParams(-1, dp(36)))
        } else {
            val older = history.filterNot { snapshot.kind == ClipboardSnapshot.Kind.TEXT && it.text == snapshot.text }
            older.forEachIndexed { index, entry ->
                val item = ui.row().apply { background = ui.round(ui.palette.key, border = true) }
                val text = ui.text(entry.text.take(1000), 14f).apply {
                    maxLines = 3; ellipsize = android.text.TextUtils.TruncateAt.END
                    isSaveEnabled = false
                    setOnClickListener { resolveThen { commitText(entry.text) } }
                    contentDescription = "粘贴历史第${index + 1}项"
                }
                item.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
                item.addView(ui.button("粘贴") { resolveThen { commitText(entry.text) } }.apply {
                    textSize = 12f; contentDescription = "粘贴历史第${index + 1}项"
                }, LinearLayout.LayoutParams(dp(48), dp(44)))
                item.addView(ui.button("删除") { store.deleteClipboardEntry(entry.id); renderBody() }.apply {
                    textSize = 12f; contentDescription = "删除历史第${index + 1}项"
                }, LinearLayout.LayoutParams(dp(48), dp(44)))
                contents.addView(item, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
            }
            // The current value is shown only once, with its own history deletion.
            history.firstOrNull { it.text == snapshot.text }?.let { currentEntry ->
                contents.addView(ui.button("删除当前项") {
                    clipboard.clear(); store.deleteClipboardEntry(currentEntry.id)
                    releaseClip(); refreshClipboard(); renderBody()
                }.apply { textSize = 12f }, LinearLayout.LayoutParams(-1, dp(36)))
            }
            if (history.isEmpty()) contents.addView(ui.text("暂无历史，复制普通文本后会自动记录", 12f, ui.palette.muted))
        }
        scroll.addView(contents)
        container.addView(scroll, LinearLayout.LayoutParams(-1, dp(keyHeight * 4)).apply { setMargins(dp(3), dp(6), dp(3), dp(6)) })
        container.addView(ui.text("最近 10 项仅存本机 · 敏感内容不入历史", 11f, ui.palette.muted))
    }

    private fun showCursorControls() {
        resolveThen {
            body?.removeAllViews()
            val row = ui.row()
            listOf("←" to KeyEvent.KEYCODE_DPAD_LEFT, "↑" to KeyEvent.KEYCODE_DPAD_UP, "↓" to KeyEvent.KEYCODE_DPAD_DOWN, "→" to KeyEvent.KEYCODE_DPAD_RIGHT).forEach { (label, key) ->
                ui.addWeighted(row, ui.button(label) {
                    currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
                    currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
                }, height = 52)
            }
            body?.addView(row)
            body?.addView(ui.button("返回键盘") { renderBody() }, LinearLayout.LayoutParams(-1, dp(44)))
        }
    }
    private fun vibrate() {
        if (!haptics || SystemClock.uptimeMillis() - lastVibration < 30) return
        lastVibration = SystemClock.uptimeMillis()
        getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE))
    }
    private fun hint(text: String) {
        if (candidateRow?.visibility == View.VISIBLE && candidateRow?.showHint(text) == true) return
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        destroyed = true
        invalidateSession()
        clipboard.stop()
        // The platform's onDestroy invokes onFinishInput. Keep the worker open
        // until those callbacks have finished, and reject further UI work.
        super.onDestroy()
        worker.execute { repository?.close(); pronunciation?.close(); store.close() }
        worker.shutdown()
        main.removeCallbacksAndMessages(null)
    }
}
