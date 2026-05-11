package com.cesia.input.engine

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.view.inputmethod.InputConnection
import android.widget.Toast
import com.cesia.input.polish.PolishService
import com.cesia.input.recognizer.FallbackRecognizer
import com.cesia.input.wakeword.WakeWordDetector
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import android.util.Log

/**
 * Typeless 引擎 —— 核心编排层
 *
 * 工作流程 (纯 SpeechRecognizer 方案):
 *
 * === 语音激活模式 (WakeWord) ===
 * 1. 后台循环监听 "Hey Typeless" (WakeWordDetector)
 * 2. 检测到唤醒词 → 切换到 ACTIVE 录音模式
 * 3. 用户说话 → 系统自带 VAD 检测静音结束
 * 4. 检测到结束词 "Typeless Over" → 去除结束词 → 提交文本
 * 5. 文本发送到润色 API
 * 6. 润色结果自动上屏 (commitText)
 * 7. 回到步骤1继续监听
 *
 * === 手动按钮模式 ===
 * 1. 用户按麦克风按钮 → SpeechRecognizer.startListening()
 * 2. 系统 VAD 检测静音结束 → onResults 回调
 * 3. 文本发送到润色 API
 * 4. 润色结果自动上屏 (commitText)
 *
 * 设计目标: 3 步完成输入 —— 说"Hey Typeless" → 说话 → 说"Typeless Over"→ 自动上屏
 */
class TypelessEngine(
    private val context: Context,
    private val service: InputMethodService
) {
    private val engineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var polishService: PolishService? = null
    private var fallbackRecognizer: FallbackRecognizer? = null
    private var wakeWordDetector: WakeWordDetector? = null

    // 识别器是否可用
    private var recognizerAvailable = false

    // 引擎状态
    private var _state = MutableStateFlow(State.IDLE)
    val state = _state.asStateFlow()

    enum class State {
        IDLE,          // 空闲
        MONITORING,    // 后台监听唤醒词
        LISTENING,     // 正在录音/识别
        PROCESSING,    // 正在润色
        COMMITTING,    // 正在上屏
        ERROR          // 出错
    }

    // 日志回调
    var onLogMessage: ((String) -> Unit)? = null

    // 语音激活开关
    var voiceActivationEnabled: Boolean = false
        set(value) {
            field = value
            if (value) {
                startWakeWordMonitoring()
            } else {
                wakeWordDetector?.stop()
                if (_state.value == State.MONITORING) {
                    _state.value = State.IDLE
                }
            }
        }

    init {
        // 启动识别结果监听协程
        engineScope.launch {
            while (fallbackRecognizer == null) {
                delay(50)
            }

            fallbackRecognizer?.results?.collect { result ->
                when (result) {
                    is FallbackRecognizer.Result.Success -> {
                        log("📝 识别成功: ${result.text.take(50)}...")
                        polishAndCommit(result.text)
                    }
                    is FallbackRecognizer.Result.Partial -> {
                        log("📝 部分识别: ${result.text}")
                    }
                    is FallbackRecognizer.Result.Error -> {
                        log("❌ 识别错误: ${result.message}")
                        _state.value = State.ERROR
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                "语音识别失败: ${result.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    is FallbackRecognizer.Result.NoMatch -> {
                        log("❓ 未识别到语音")
                        _state.value = State.IDLE
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "未识别到语音，请重试", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                }
            }
        }
    }

    // =====================
    // 初始化
    // =====================

    fun initialize() {
        polishService = PolishService(engineScope)

        fallbackRecognizer = FallbackRecognizer(context).also { recognizer ->
            recognizerAvailable = recognizer.init()
            if (recognizerAvailable) {
                log("✅ 系统语音识别已就绪")
            } else {
                log("⚠️ 系统语音识别不可用")
            }
        }

        // 初始化唤醒词检测器
        wakeWordDetector = WakeWordDetector(context, engineScope).also { detector ->
            detector.voiceActivationEnabled = false // 默认关闭，由用户设置

            engineScope.launch {
                detector.events.collect { event ->
                    when (event) {
                        is WakeWordDetector.Event.Ready -> {
                            log("🎙️ 唤醒词检测器已就绪")
                        }
                        is WakeWordDetector.Event.WakeWordDetected -> {
                            log("🔔 唤醒词检测到！开始录音...")
                            _state.value = State.LISTENING

                            // 停止监控 recognizer，启动 active recognizer
                            if (recognizerAvailable) {
                                fallbackRecognizer?.startListening()
                            }
                        }
                        is WakeWordDetector.Event.EndWordDetected -> {
                            log("🏁 结束词检测到: ${event.text.take(30)}...")
                            polishAndCommit(event.text)
                            _state.value = State.PROCESSING
                        }
                        is WakeWordDetector.Event.PartialText -> {
                            log("📝 实时识别: ${event.text}")
                        }
                        is WakeWordDetector.Event.Error -> {
                            log("❌ 唤醒词检测错误: ${event.message}")
                            _state.value = State.ERROR
                        }
                    }
                }
            }
        }
    }

    // =====================
    // 语音识别 (手动按钮触发)
    // =====================

    fun startListening() {
        if (_state.value == State.LISTENING) return

        if (!recognizerAvailable) {
            log("❌ 语音识别不可用")
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "语音识别不可用，请检查设备",
                    Toast.LENGTH_SHORT
                ).show()
            }
            return
        }

        _state.value = State.LISTENING
        log("🎤 开始语音识别...")
        fallbackRecognizer?.startListening()
    }

    // =====================
    // 唤醒词监控 (语音激活)
    // =====================

    private fun startWakeWordMonitoring() {
        if (!voiceActivationEnabled) return

        _state.value = State.MONITORING
        log("🔇 进入唤醒词监听模式: \"${wakeWordDetector?.wakeWord}\"")
        wakeWordDetector?.start()
    }

    // =====================
    // 文本润色 + 上屏
    // =====================

    private fun polishAndCommit(rawText: String) {
        engineScope.launch {
            _state.value = State.PROCESSING
            log("🔄 正在润色: ${rawText.take(30)}...")

            val polishService = polishService
            if (polishService == null) {
                log("⚠️ 润色服务未初始化，直接上屏")
                commitTextToEditor(rawText)
                return@launch
            }

            val result = polishService.polishText(rawText)
            when (result) {
                is PolishService.PolishResult.Success -> {
                    _state.value = State.COMMITTING
                    val polished =
                        if (result.polishedText.isBlank()) result.originalText else result.polishedText
                    log("✅ 润色完成: $polished")
                    commitTextToEditor(polished)
                }
                is PolishService.PolishResult.Error -> {
                    log("❌ 润色失败: ${result.message}")
                    commitTextToEditor(rawText)
                }
                PolishService.PolishResult.EmptyInput -> {
                    log("⚠️ 空识别结果，跳过")
                    _state.value = State.IDLE
                }
            }
        }
    }

    private fun commitTextToEditor(text: String) {
        engineScope.launch {
            _state.value = State.COMMITTING

            val conn = service.currentInputConnection ?: run {
                log("❌ 无法获取 InputConnection")
                _state.value = State.IDLE
                return@launch
            }

            conn.finishComposingText()

            val maxChunk = 200
            if (text.length <= maxChunk) {
                conn.commitText(text, 1)
            } else {
                var remaining = text
                while (remaining.isNotEmpty()) {
                    val chunk = remaining.substring(0, kotlin.math.min(maxChunk, remaining.length))
                    conn.commitText(chunk, 1)
                    remaining = remaining.substring(chunk.length)
                    delay(50)
                }
            }

            log("✅ 已上屏: ${text.take(50)}...")
            _state.value = State.IDLE

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "✓ ${text.take(30)}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // =====================
    // 控制方法
    // =====================

    fun stopListening() {
        fallbackRecognizer?.stopListening()
        log("⏹️ 语音识别已停止")
    }

    fun destroy() {
        engineScope.cancel()
        fallbackRecognizer?.destroy()
        polishService?.shutdown()
        wakeWordDetector?.stop()
        log("引擎已销毁")
    }

    fun updateApiUrl(url: String) {
        polishService?.updateApiUrl(url)
        log("API URL 已更新")
    }

    /**
     * 设置唤醒词和结束词
     */
    fun setWakeWord(word: String) {
        wakeWordDetector?.wakeWord = word
        log("唤醒词: $word")
    }

    fun setEndWord(word: String) {
        wakeWordDetector?.endWord = word
        log("结束词: $word")
    }

    /**
     * 获取当前状态文本
     */
    fun getStateInfo(): String {
        return when (_state.value) {
            State.IDLE -> "就绪"
            State.MONITORING -> "🔇 监听唤醒词..."
            State.LISTENING -> "🎤 识别中..."
            State.PROCESSING -> "🔄 润色中..."
            State.COMMITTING -> "⬆️ 上屏中..."
            State.ERROR -> "❌ 出错"
        }
    }

    private fun log(msg: String) {
        Log.d("TypelessEngine", msg)
        onLogMessage?.invoke(msg)
    }
}