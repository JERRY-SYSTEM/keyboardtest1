package com.cesia.input

import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.cesia.input.engine.PinyinEngine
import com.cesia.input.engine.TypelessEngine
import com.cesia.input.stats.PolishStatsManager
import com.google.android.material.button.MaterialButton
import android.widget.Toast

/**
 * Cesia 输入法 — 语音自动润色上屏 + 中文拼音输入
 *
 * 键盘布局：
 * - 第一行：数字 1-0
 * - 第二至四行：QWERTY 字母键，每个键有小符号标注
 * - 第五行：符号切换 + 中/英切换 + 标点 + 空格 + 回车 + 发送
 *
 * 功能：
 * - 点击符号键 → 切换到符号键盘
 * - 长按字母键 → Fn 效果，输出对应符号
 * - 退格键长按 → 快速连续删除
 * - 中/英切换 → 中文拼音输入模式
 * - 左下角魔法按钮 → 语音指令修改文字
 */
class CesiaInputMethod : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    // 视图
    private lateinit var keyboardView: com.cesia.input.ui.CustomKeyboardView
    private lateinit var qwertyKeyboard: Keyboard
    private lateinit var symbolKeyboard: Keyboard
    private var currentKeyboard: Keyboard? = null

    private lateinit var micButton: MaterialButton
    private lateinit var btnSettings: ImageButton
    private lateinit var btnDelete: ImageButton
    private lateinit var btnClipboard: ImageButton
    private lateinit var btnMagic: com.google.android.material.button.MaterialButton
    private lateinit var statusDot: View
    private lateinit var statusText: TextView

    // 候选词栏
    private lateinit var candidateBar: LinearLayout
    private lateinit var tvComposing: TextView
    private lateinit var tvCandidates: Array<TextView>
    private lateinit var btnCandidatePrev: ImageButton
    private lateinit var btnCandidateNext: ImageButton

    // 核心组件
    private var typelessEngine: TypelessEngine? = null
    private lateinit var statsManager: PolishStatsManager
    private lateinit var pinyinEngine: PinyinEngine

    // 状态
    private var isRecording = false
    private var isSymbolMode = false
    private var isCapsLock = false
    private var isChineseMode = false
    private var isProcessingResult = false  // 正在处理识别结果（润色中）
    private var lastMicClickTime = 0L  // 防抖
    private var voiceStartTime = 0L  // 语音开始时间

    // 长按检测
    private var longPressHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var currentLongPressKey: Keyboard.Key? = null
    private var longPressTriggered = false

    // 退格键长按连续删除
    private var backspaceHandler = Handler(Looper.getMainLooper())
    private var backspaceRunnable: Runnable? = null

    // 魔法模式
    private var magicMode = false
    private var magicOriginalText = ""

    // AI自动回复
    private var aiReplyStyle = "自然" // 自然, 幽默, 圆滑, 官方, 简洁
    private var isAiProcessing = false

    // 主题
    private var isDarkTheme = false

    // 设置
    private var apiUrl = "https://typeless-ai-service.vercel.app/api/polish"

    companion object {
        const val PREF_API_URL = "api_url"
        const val PREF_THEME_MODE = "theme_mode"
        const val PREF_AI_STYLE = "ai_reply_style"
        const val DEFAULT_API_URL = "https://typeless-ai-service.vercel.app/api/polish"
        const val KEYCODE_SWITCH_SYMBOL = -100
        const val KEYCODE_SWITCH_LANG = -101
        const val THEME_LIGHT = 0
        const val THEME_DARK = 1
    }

    override fun onCreate() {
        // 读取主题设置
        val themeMode = getSharedPreferences("cesia_settings", MODE_PRIVATE)
            .getInt(PREF_THEME_MODE, THEME_LIGHT)
        isDarkTheme = themeMode == THEME_DARK
        setTheme(if (isDarkTheme) R.style.Theme_Cesia_Dark else R.style.Theme_Cesia)
        super.onCreate()
    }

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.input_view, null)

        keyboardView = view.findViewById(R.id.keyboard_view)
        micButton = view.findViewById(R.id.btn_mic)
        btnSettings = view.findViewById(R.id.btn_settings)
        btnDelete = view.findViewById(R.id.btn_delete)
        btnClipboard = view.findViewById(R.id.btn_clipboard)
        btnMagic = view.findViewById(R.id.btn_magic)
        statusDot = view.findViewById(R.id.v_status_dot)
        statusText = view.findViewById(R.id.tv_status)

        // 候选词栏
        candidateBar = view.findViewById(R.id.candidate_bar)
        tvComposing = view.findViewById(R.id.tv_composing)
        tvCandidates = arrayOf(
            view.findViewById<TextView>(R.id.tv_candidate_1),
            view.findViewById<TextView>(R.id.tv_candidate_2),
            view.findViewById<TextView>(R.id.tv_candidate_3),
            view.findViewById<TextView>(R.id.tv_candidate_4),
            view.findViewById<TextView>(R.id.tv_candidate_5)
        )
        btnCandidatePrev = view.findViewById(R.id.btn_candidate_prev)
        btnCandidateNext = view.findViewById(R.id.btn_candidate_next)

        // 初始化键盘
        qwertyKeyboard = Keyboard(this, R.xml.qwerty)
        symbolKeyboard = Keyboard(this, R.xml.symbols)
        currentKeyboard = qwertyKeyboard

        keyboardView.keyboard = currentKeyboard
        keyboardView.setOnKeyboardActionListener(this)
        keyboardView.isPreviewEnabled = true

        // 初始化引擎
        statsManager = PolishStatsManager(this)
        pinyinEngine = PinyinEngine(this)
        typelessEngine = TypelessEngine(this, this).also { engine ->
            engine.onLogMessage = { msg ->
                Handler(Looper.getMainLooper()).post { updateStatus(msg) }
            }
            engine.onMagicResult = { recognizedText ->
                Handler(Looper.getMainLooper()).post {
                    handleMagicResult(recognizedText)
                }
            }
            engine.onPolishComplete = { inputText, outputText, _ ->
                val duration = if (voiceStartTime > 0) System.currentTimeMillis() - voiceStartTime else 0
                statsManager.addRecord(inputText, outputText, duration)
            }
            // 识别结果返回时标记为处理中
            engine.onResultProcessing = {
                Handler(Looper.getMainLooper()).post {
                    isProcessingResult = true
                    setStatusDot("processing")
                }
            }
            // 润色完成时取消处理中标记
            engine.onResultCommitted = {
                Handler(Looper.getMainLooper()).post {
                    isProcessingResult = false
                    isRecording = false
                    micButton.isActivated = false
                    micButton.text = "🎤 点击开始说话"
                    setStatusDot("idle")
                    updateStatus("✅ 润色完成")
                }
            }
            engine.initialize()
        }

        loadSettings()
        // 加载AI回复风格
        aiReplyStyle = getSharedPreferences("cesia_settings", MODE_PRIVATE)
            .getString(PREF_AI_STYLE, "自然") ?: "自然"
        setupButtonListeners()
        setupCandidateBar()

        // 应用主题到键盘视图
        applyKeyboardTheme()

        updateStatus("Cesia 已就绪")
        setStatusDot("idle")

        return view
    }

    private fun applyKeyboardTheme() {
        if (isDarkTheme) {
            keyboardView.setBackgroundColor(0xFF0F0F23.toInt())
            keyboardView.keyTextColor = 0xFFE0E0E0.toInt()
            // 状态栏
            (statusText.parent as? View)?.setBackgroundColor(0xFF1A1A2E.toInt())
            statusText.setTextColor(0xFFE0E0E0.toInt())
            // 候选词栏
            candidateBar.setBackgroundColor(0xFF16213E.toInt())
            tvComposing.setTextColor(0xFF4488FF.toInt())
            for (tv in tvCandidates) {
                tv.setTextColor(0xFFE0E0E0.toInt())
            }
            // 底部按钮栏
            (btnClipboard.parent as? View)?.setBackgroundColor(0xFF1A1A2E.toInt())
        } else {
            keyboardView.setBackgroundColor(0xFFE8E8E8.toInt())
            keyboardView.keyTextColor = 0xFF333333.toInt()
            (statusText.parent as? View)?.setBackgroundColor(0xFFEEEEEE.toInt())
            statusText.setTextColor(0xFF555555.toInt())
            candidateBar.setBackgroundColor(0xFFF0F0F0.toInt())
            tvComposing.setTextColor(0xFF4488FF.toInt())
            for (tv in tvCandidates) {
                tv.setTextColor(0xFF333333.toInt())
            }
            (btnClipboard.parent as? View)?.setBackgroundColor(0xFFE0E0E0.toInt())
        }
    }

    private fun setupCandidateBar() {
        // 候选词点击
        for (i in tvCandidates.indices) {
            val index = i
            tvCandidates[i].setOnClickListener {
                if (isChineseMode && pinyinEngine.hasCandidates()) {
                    val selected = pinyinEngine.selectCandidate(
                        pinyinEngine.getCurrentPage() * 5 + index
                    )
                    if (selected.isNotEmpty()) {
                        currentInputConnection?.commitText(selected, 1)
                        updateCandidateBar()
                    }
                }
            }
        }

        // 翻页按钮
        btnCandidatePrev.setOnClickListener {
            if (isChineseMode && pinyinEngine.hasCandidates()) {
                pinyinEngine.prevPage()
                updateCandidateBar()
            }
        }

        btnCandidateNext.setOnClickListener {
            if (isChineseMode && pinyinEngine.hasCandidates()) {
                pinyinEngine.nextPage()
                updateCandidateBar()
            }
        }
    }

    private fun updateCandidateBar() {
        if (!isChineseMode || !pinyinEngine.isComposing()) {
            candidateBar.visibility = View.GONE
            return
        }

        val candidates = pinyinEngine.getCandidates()
        if (candidates.isEmpty()) {
            candidateBar.visibility = View.GONE
            return
        }

        candidateBar.visibility = View.VISIBLE
        tvComposing.text = pinyinEngine.getCurrentPinyin()

        for (i in tvCandidates.indices) {
            if (i < candidates.size) {
                tvCandidates[i].text = candidates[i]
                tvCandidates[i].visibility = View.VISIBLE
            } else {
                tvCandidates[i].visibility = View.INVISIBLE
            }
        }

        // 更新翻页按钮状态
        btnCandidatePrev.isEnabled = pinyinEngine.getCurrentPage() > 0
        btnCandidateNext.isEnabled = pinyinEngine.getCurrentPage() < pinyinEngine.getPageCount() - 1
    }

    private fun setupButtonListeners() {
        micButton.setOnClickListener { toggleRecording() }
        micButton.setOnLongClickListener {
            if (isRecording) { stopRecording(); true } else false
        }

        btnSettings.setOnClickListener { showSettings() }

        btnDelete.setOnClickListener {
            if (isChineseMode && pinyinEngine.isComposing()) {
                handleChineseBackspace()
            } else {
                currentInputConnection?.deleteSurroundingText(Integer.MAX_VALUE, 0)
            }
        }

        // AI自动回复按钮：短按=读取上下文并生成回复，长按=切换语言风格
        btnClipboard.setOnClickListener { triggerAiReply() }
        btnClipboard.setOnLongClickListener { showAiStylePicker(); true }

        btnMagic.setOnClickListener { startMagicMode() }
    }

    // ======================== AI自动回复 ========================

    private fun showAiStylePicker() {
        val styles = arrayOf("自然", "幽默", "圆滑", "官方", "简洁", "正式", "亲切", "犀利")
        AlertDialog.Builder(this)
            .setTitle("🎭 选择AI回复风格")
            .setItems(styles) { _, which ->
                aiReplyStyle = styles[which]
                getSharedPreferences("cesia_settings", MODE_PRIVATE)
                    .edit().putString(PREF_AI_STYLE, aiReplyStyle).apply()
                updateStatus("✅ 已切换为「$aiReplyStyle」风格")
                Toast.makeText(this, "已切换为「$aiReplyStyle」风格", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun triggerAiReply() {
        if (isAiProcessing) {
            updateStatus("⏳ AI正在处理中，请稍候...")
            return
        }

        val ic = currentInputConnection ?: run {
            updateStatus("❌ 无输入框连接")
            return
        }

        // 读取上下文：获取输入框前后文字
        val textBefore = ic.getTextBeforeCursor(2000, 0)?.toString() ?: ""
        val textAfter = ic.getTextAfterCursor(2000, 0)?.toString() ?: ""

        if (textBefore.isEmpty() && textAfter.isEmpty()) {
            updateStatus("⚠️ 输入框为空，无法分析上下文")
            return
        }

        isAiProcessing = true
        updateStatus("🤖 AI正在分析上下文...")
        setStatusDot("processing")

        // 构建AI提示
        val context = buildContext(textBefore, textAfter)
        val prompt = buildAiReplyPrompt(context, aiReplyStyle)

        // 调用润色API进行AI回复生成
        val polishService = typelessEngine?.getPolishService()
        Thread {
            try {
                val result = polishService?.polishWithPrompt(prompt)
                Handler(Looper.getMainLooper()).post {
                    isAiProcessing = false
                    setStatusDot("idle")
                    if (result != null && result.isNotEmpty()) {
                        // 将AI生成的回复上屏
                        ic.commitText(result, 1)
                        updateStatus("✅ AI回复已生成（$aiReplyStyle 风格）")
                    } else {
                        updateStatus("⚠️ AI未生成有效回复")
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    isAiProcessing = false
                    setStatusDot("idle")
                    updateStatus("❌ AI回复失败: ${e.message}")
                }
            }
        }.start()
    }

    private fun buildContext(before: String, after: String): String {
        val sb = StringBuilder()
        if (before.isNotEmpty()) {
            sb.append("【前面已有的内容】\n$before\n\n")
        }
        if (after.isNotEmpty()) {
            sb.append("【后面已有的内容】\n$after\n\n")
        }
        return sb.toString()
    }

    private fun buildAiReplyPrompt(context: String, style: String): String {
        val styleDesc = when (style) {
            "幽默" -> "用幽默风趣的方式回复，适当使用俏皮话和轻松的语气"
            "圆滑" -> "用圆滑得体的方式回复，措辞委婉，不得罪人"
            "官方" -> "用官方正式的语气回复，措辞严谨规范"
            "简洁" -> "用简洁明了的方式回复，言简意赅，不废话"
            "正式" -> "用正式商务的语气回复，专业得体"
            "亲切" -> "用亲切温暖的方式回复，语气温和友好"
            "犀利" -> "用犀利直接的方式回复，观点鲜明，一针见血"
            else -> "用自然流畅的方式回复，语气自然"
        }
        return "你是一个智能回复助手。请根据以下聊天上下文，生成一条合适的回复。\n\n" +
                "要求：$styleDesc\n" +
                "只输出回复内容本身，不要解释。\n\n" +
                "$context\n" +
                "请生成合适的回复："
    }

    private fun showSettings() {
        Intent(this, SettingsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(this)
        }
    }

    private fun loadSettings() {
        try {
            val prefs = getSharedPreferences("cesia_settings", MODE_PRIVATE)
            apiUrl = prefs.getString(PREF_API_URL, DEFAULT_API_URL) ?: DEFAULT_API_URL
            typelessEngine?.updateApiUrl(apiUrl)
        } catch (_: Exception) {
            apiUrl = DEFAULT_API_URL
        }
    }

    // ======================== 中/英切换 ========================

    private fun toggleChineseMode() {
        isChineseMode = !isChineseMode
        if (isChineseMode) {
            // 切换到中文模式
            isSymbolMode = false
            currentKeyboard = qwertyKeyboard
            keyboardView.keyboard = qwertyKeyboard
            keyboardView.invalidateAllKeys()
            pinyinEngine.clear()
            candidateBar.visibility = View.GONE
            updateStatus("中文拼音模式")
        } else {
            // 切换到英文模式
            pinyinEngine.clear()
            candidateBar.visibility = View.GONE
            updateStatus("英文模式")
        }
    }

    // ======================== 中文拼音输入处理 ========================

    private fun handleChineseInput(primaryCode: Int) {
        val c = primaryCode.toChar()

        if (c in 'a'..'z') {
            // 输入拼音字母
            val pinyin = pinyinEngine.inputLetter(c)
            updateCandidateBar()
            // 显示拼音串在状态栏
            updateStatus("拼音: $pinyin")
        } else if (c == ' ') {
            // 空格：选择第一个候选词或直接输入空格
            if (pinyinEngine.hasCandidates()) {
                val selected = pinyinEngine.selectCandidate(0)
                currentInputConnection?.commitText(selected, 1)
            } else {
                currentInputConnection?.commitText(" ", 1)
            }
            pinyinEngine.clear()
            updateCandidateBar()
        } else {
            // 其他字符（数字、标点等）：如果有候选词先上屏，再输出字符
            if (pinyinEngine.isComposing()) {
                val selected = if (pinyinEngine.hasCandidates()) {
                    pinyinEngine.selectCandidate(0)
                } else {
                    pinyinEngine.getCurrentPinyin()
                }
                currentInputConnection?.commitText(selected, 1)
                pinyinEngine.clear()
                updateCandidateBar()
            }
            // 输出字符（中文模式下自动转换标点）
            val charStr: String = if (isChineseMode) {
                when (c) {
                    ',' -> "\uFF0C"
                    '.' -> "\u3002"
                    '!' -> "\uFF01"
                    '?' -> "\uFF1F"
                    ';' -> "\uFF1B"
                    ':' -> "\uFF1A"
                    '(' -> "\uFF08"
                    ')' -> "\uFF09"
                    '[' -> "\u3010"
                    ']' -> "\u3011"
                    '{' -> "\uFF5B"
                    '}' -> "\uFF5D"
                    '<' -> "\u300A"
                    '>' -> "\u300B"
                    '"' -> "\u300C"
                    '\u0027' -> "\u300E"
                    '\\' -> "\u3001"
                    '|' -> "\uFF5C"
                    '~' -> "\uFF5E"
                    '`' -> "\u00B7"
                    else -> c.toString()
                }
            } else {
                c.toString()
            }
            currentInputConnection?.commitText(charStr, 1)
        }
    }

    private fun handleChineseBackspace() {
        if (pinyinEngine.isComposing()) {
            val pinyin = pinyinEngine.backspace()
            if (pinyin.isEmpty()) {
                pinyinEngine.clear()
                candidateBar.visibility = View.GONE
                updateStatus("中文拼音模式")
            } else {
                updateCandidateBar()
                updateStatus("拼音: $pinyin")
            }
        } else {
            currentInputConnection?.deleteSurroundingText(1, 0)
        }
    }

    // ======================== 录音 ========================

    private fun toggleRecording() {
        // 防抖：300ms内不重复响应
        val now = System.currentTimeMillis()
        if (now - lastMicClickTime < 300) return
        lastMicClickTime = now

        if (isRecording) {
            // 如果正在处理识别结果，不中断，等其自然完成
            if (isProcessingResult) {
                updateStatus("⏳ 正在润色中，请稍候...")
                return
            }
            stopRecording()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        isRecording = true
        micButton.isActivated = true
        micButton.text = "⏹️ 再次点击完成"
        setStatusDot("recording")
        // 录音时隐藏键盘区域，状态栏扩展覆盖
        keyboardView.visibility = View.GONE
        candidateBar.visibility = View.GONE
        voiceStartTime = System.currentTimeMillis()
        updateStatus("🎤 正在收听，请说话...")
        typelessEngine?.startListening(continuous = true)
    }

    private fun stopRecording() {
        isRecording = false
        micButton.isActivated = false
        micButton.text = "🎤 点击开始说话"
        setStatusDot("idle")
        typelessEngine?.stopListening()
        // 恢复键盘区域
        keyboardView.visibility = View.VISIBLE
        updateStatus("Cesia 已就绪")
    }

    // ======================== 魔法模式 ========================

    private fun startMagicMode() {
        val ic = currentInputConnection ?: run {
            updateStatus("❌ 无输入框连接")
            return
        }
        val extracted = ic.getTextBeforeCursor(10000, 0)?.toString() ?: ""
        val extractedAfter = ic.getTextAfterCursor(10000, 0)?.toString() ?: ""
        val fullText = extracted + extractedAfter

        if (fullText.isEmpty()) {
            updateStatus("⚠️ 输入框无文字，无法修改")
            return
        }

        magicOriginalText = fullText
        magicMode = true
        typelessEngine?.magicMode = true

        updateStatus("🎤 请说出修改指令...")
        setStatusDot("recording")
        isRecording = true
        micButton.isActivated = true
        micButton.text = "⏹️ 再次点击完成"

        typelessEngine?.startListening(continuous = true)
    }

    private fun handleMagicResult(recognizedText: String) {
        magicMode = false
        typelessEngine?.magicMode = false
        isRecording = false
        micButton.isActivated = false
        micButton.text = "🎤 点击开始说话"
        setStatusDot("idle")

        val instruction = recognizedText.trim()
        if (instruction.isEmpty()) {
            updateStatus("⚠️ 未识别到指令")
            return
        }

        Log.d("CesiaMagic", "原文: $magicOriginalText")
        Log.d("CesiaMagic", "指令: $instruction")
        updateStatus("🔄 正在处理修改指令...")

        val prompt = buildMagicPrompt(magicOriginalText, instruction)
        val polishService = typelessEngine?.getPolishService()

        Thread {
            try {
                val result = polishService?.polishWithPrompt(prompt)
                Handler(Looper.getMainLooper()).post {
                    if (result != null && result.isNotEmpty() && result != magicOriginalText) {
                        replaceInputText(result)
                        updateStatus("✅ 修改完成")
                    } else {
                        updateStatus("⚠️ 修改结果为空或无变化")
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    updateStatus("❌ 修改失败: ${e.message}")
                }
            }
        }.start()
    }

    private fun buildMagicPrompt(original: String, instruction: String): String {
        return "原文：$original\n\n用户指令：$instruction\n\n请根据用户指令修改原文，输出修改后的完整文本。只输出修改后的文本，不要输出任何解释。"
    }

    private fun replaceInputText(newText: String) {
        val ic = currentInputConnection ?: return
        ic.performContextMenuAction(android.R.id.selectAll)
        ic.commitText(newText, 1)
    }

    // ======================== 键盘切换 ========================

    private fun switchToSymbolKeyboard() {
        if (!isSymbolMode) {
            isSymbolMode = true
            isChineseMode = false
            pinyinEngine.clear()
            candidateBar.visibility = View.GONE
            currentKeyboard = symbolKeyboard
            keyboardView.keyboard = symbolKeyboard
            keyboardView.invalidateAllKeys()
        }
    }

    private fun switchToQwertyKeyboard() {
        if (isSymbolMode) {
            isSymbolMode = false
            currentKeyboard = qwertyKeyboard
            keyboardView.keyboard = qwertyKeyboard
            keyboardView.invalidateAllKeys()
        }
    }

    private fun toggleKeyboard() {
        if (isSymbolMode) switchToQwertyKeyboard() else switchToSymbolKeyboard()
    }

    // ======================== 长按 Fn 效果 ========================

    private fun startLongPressDetection(key: Keyboard.Key) {
        cancelLongPress()
        currentLongPressKey = key
        longPressRunnable = Runnable {
            val popup = key.popupCharacters
            if (!popup.isNullOrEmpty()) {
                val symbol = popup[0].toString()
                currentInputConnection?.commitText(symbol, 1)
                Log.d("Cesia", "Fn 长按输出: $symbol")
                keyboardView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                longPressTriggered = true
            }
            currentLongPressKey = null
        }.also {
            longPressHandler.postDelayed(it, 400)
        }
    }

    private fun cancelLongPress() {
        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
        longPressRunnable = null
        currentLongPressKey = null
        longPressTriggered = false
    }

    // ======================== KeyboardView 回调 ========================

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        // 先检查是否长按触发过，再cancel
        val wasLongPressed = longPressTriggered
        cancelLongPress()

        if (wasLongPressed) {
            return
        }

        when (primaryCode) {
            KEYCODE_SWITCH_SYMBOL -> {
                toggleKeyboard()
            }
            KEYCODE_SWITCH_LANG -> {
                toggleChineseMode()
            }
            -1 -> { // Shift
                isCapsLock = !isCapsLock
                qwertyKeyboard.isShifted = isCapsLock
                keyboardView.invalidateAllKeys()
            }
            -5 -> { // 删除
                if (isChineseMode) {
                    handleChineseBackspace()
                } else {
                    currentInputConnection?.deleteSurroundingText(1, 0)
                }
            }
            -200 -> { // 发送
                // 如果有未上屏的拼音，先上屏
                if (isChineseMode && pinyinEngine.isComposing()) {
                    val text = if (pinyinEngine.hasCandidates()) {
                        pinyinEngine.selectCandidate(0)
                    } else {
                        pinyinEngine.getCurrentPinyin()
                    }
                    currentInputConnection?.commitText(text, 1)
                    pinyinEngine.clear()
                    updateCandidateBar()
                }
                currentInputConnection?.apply {
                    sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                    sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                }
            }
            Keyboard.KEYCODE_DELETE -> {
                if (isChineseMode) {
                    handleChineseBackspace()
                } else {
                    currentInputConnection?.deleteSurroundingText(1, 0)
                }
            }
            Keyboard.KEYCODE_SHIFT -> {
                isCapsLock = !isCapsLock
                qwertyKeyboard.isShifted = isCapsLock
                keyboardView.invalidateAllKeys()
            }
            Keyboard.KEYCODE_DONE -> {
                currentInputConnection?.apply {
                    sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                    sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                }
            }
            Keyboard.KEYCODE_MODE_CHANGE -> {
                toggleKeyboard()
            }
            else -> {
                if (isChineseMode && primaryCode in 97..122) {
                    // 中文模式：字母键走拼音输入
                    handleChineseInput(primaryCode)
                } else {
                    // 普通字符键（中文模式下自动转换标点）
                    var char = primaryCode.toChar()
                    if (isCapsLock && char.isLowerCase()) {
                        char = char.uppercaseChar()
                    }
                    val charStr: String = if (isChineseMode) {
                        when (char) {
                            ',' -> "，"
                            '.' -> "。"
                            '!' -> "！"
                            '?' -> "？"
                            ';' -> "；"
                            ':' -> "："
                            '(' -> "（"
                            ')' -> "）"
                            '[' -> "【"
                            ']' -> "】"
                            '{' -> "｛"
                            '}' -> "｝"
                            '<' -> "《"
                            '>' -> "》"
                            '\\' -> "\u3001"
                            '\u0027' -> "『"
                            '\\' -> "\u3001"
                            '|' -> "｜"
                            '~' -> "～"
                            '`' -> "·"
                            else -> char.toString()
                        }
                    } else {
                        char.toString()
                    }
                    currentInputConnection?.commitText(charStr, 1)
                }
            }
        }
    }

    override fun onPress(primaryCode: Int) {
        // 检测长按 Fn 效果（仅在英文模式）
        if (primaryCode > 0 && !isSymbolMode && !isChineseMode) {
            val key = currentKeyboard?.keys?.find { it.codes?.contains(primaryCode) == true }
            if (key != null && !key.popupCharacters.isNullOrEmpty()) {
                startLongPressDetection(key)
            }
        }
        // 退格键长按连续删除
        if (primaryCode == -5 || primaryCode == Keyboard.KEYCODE_DELETE) {
            backspaceRunnable = object : Runnable {
                override fun run() {
                    if (isChineseMode) {
                        handleChineseBackspace()
                    } else {
                        currentInputConnection?.deleteSurroundingText(1, 0)
                    }
                    backspaceHandler.postDelayed(this, 80)
                }
            }
            backspaceHandler.postDelayed(backspaceRunnable!!, 400)
        }
    }

    override fun onRelease(primaryCode: Int) {
        cancelLongPress()
        backspaceRunnable?.let { backspaceHandler.removeCallbacks(it) }
        backspaceRunnable = null
    }

    override fun onText(text: CharSequence?) {
        cancelLongPress()
        currentInputConnection?.commitText(text, 1)
    }

    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeDown() {}
    override fun swipeUp() {}

    // ======================== 生命周期 ========================

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        loadSettings()
        // 重新加载主题（可能在设置中切换了）
        val themeMode = getSharedPreferences("cesia_settings", MODE_PRIVATE)
            .getInt(PREF_THEME_MODE, THEME_LIGHT)
        isDarkTheme = themeMode == THEME_DARK
        applyKeyboardTheme()
        // 重新加载AI风格
        aiReplyStyle = getSharedPreferences("cesia_settings", MODE_PRIVATE)
            .getString(PREF_AI_STYLE, "自然") ?: "自然"
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        if (finishingInput && isRecording) stopRecording()
    }

    override fun onDestroy() {
        cancelLongPress()
        typelessEngine?.destroy()
        typelessEngine = null
        super.onDestroy()
    }

    // ======================== UI 辅助 ========================

    private fun setStatusDot(state: String) {
        if (!::statusDot.isInitialized) return
        try {
            val drawableRes = when (state) {
                "recording" -> R.drawable.status_dot_recording
                "processing" -> R.drawable.status_dot_processing
                "error" -> R.drawable.status_dot_error
                else -> R.drawable.status_dot_idle
            }
            statusDot.setBackgroundResource(drawableRes)
        } catch (_: Exception) {}
    }

    private var statusLines = mutableListOf<String>()

    private fun updateStatus(msg: String) {
        try {
            if (isRecording) {
                // 录音时追加文字
                if (msg.startsWith("🎤") || msg.startsWith("⏳") || msg.startsWith("🔄") || msg.startsWith("✅")) {
                    // 状态消息，替换最后一行或添加
                    if (statusLines.isNotEmpty() && !statusLines.last().startsWith("📝")) {
                        statusLines[statusLines.size - 1] = msg
                    } else {
                        statusLines.add(msg)
                    }
                } else if (msg.startsWith("📝") || msg.startsWith("🎤")) {
                    // 识别结果，追加
                    statusLines.add(msg)
                } else {
                    // 其他消息，替换最后一行
                    if (statusLines.isNotEmpty()) {
                        statusLines[statusLines.size - 1] = msg
                    } else {
                        statusLines.add(msg)
                    }
                }
                // 限制行数
                while (statusLines.size > 20) {
                    statusLines.removeAt(0)
                }
                statusText.text = statusLines.joinToString("\n")
            } else {
                statusLines.clear()
                statusLines.add(msg)
                statusText.text = msg
            }
        } catch (_: Exception) {}
    }
}
