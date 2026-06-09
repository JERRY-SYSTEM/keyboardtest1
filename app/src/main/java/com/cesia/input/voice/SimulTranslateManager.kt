package com.cesia.input.voice

import android.content.Context
import android.util.Log
import com.cesia.input.ai.AIEngine
import com.cesia.input.engine.ai.SherpaOnnxEngine
import com.cesia.input.engine.ai.SherpaTtsEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/**
 * 同声传译管理器
 *
 * 链路：语音输入 → SherpaOnnx 流式识别（英文）→ MNN LLM 翻译（英→中）→ 系统 TTS 播放（中文）
 *
 * 使用方式：
 * 1. initialize() 初始化（传入 AIEngine）
 * 2. start() 开始同传
 * 3. stop() 停止
 * 4. release() 释放资源
 */
class SimulTranslateManager(private val context: Context) {

    companion object {
        private const val TAG = "SimulTranslate"
        private const val TRANSLATE_DELAY_MS = 400L  // 识别稳定后等待多久开始翻译（降低延迟）
        private const val MIN_TEXT_LENGTH = 2         // 最少多少个字符才翻译（英文短句如 "Hi"=2）
    }

    private val ttsEngine = SherpaTtsEngine()
    private var aiEngine: AIEngine? = null

    @Volatile
    private var isInitialized = false
    @Volatile
    private var isRunning = false

    // 翻译队列：识别结果 → 翻译 → TTS 串行执行
    private val translateChannel = Channel<String>(Channel.CONFLATED)
    private var translateJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // 当前识别状态
    private var lastRecognizedText = ""
    private var lastTranslateTime = 0L

    // 回调
    var onStatusUpdate: ((String) -> Unit)? = null
    var onRecognized: ((String) -> Unit)? = null
    var onTranslated: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    /**
     * 初始化：TTS + AI 引擎
     * 注意：TTS 初始化是异步的，在 IO 线程等待回调完成
     */
    suspend fun initialize(engine: AIEngine? = null): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "initialize: 开始初始化同传引擎")

        if (!SherpaOnnxEngine.isLibraryLoaded()) {
            val err = SherpaOnnxEngine.getLibraryLoadError() ?: "未知错误"
            Log.e(TAG, "initialize: Sherpa-onnx 库未加载: $err")
            onError?.invoke("语音引擎库未加载: $err")
            return@withContext false
        }

        aiEngine = engine

        // 检查 AI 模型是否已加载
        if (engine == null || !engine.isModelLoaded()) {
            Log.e(TAG, "initialize: AI 模型未加载")
            onError?.invoke("AI 模型未加载，请先下载并加载 AI 模型")
            return@withContext false
        }

        // 初始化系统 TTS（在 IO 线程等待回调）
        val ttsOk = ttsEngine.create(context)

        if (!ttsOk) {
            Log.e(TAG, "initialize: TTS 引擎初始化失败")
            onError?.invoke("语音播放引擎初始化失败，请检查系统语音引擎是否可用")
            return@withContext false
        }

        isInitialized = true
        Log.i(TAG, "initialize: 同传初始化完成 ✓")
        withContext(Dispatchers.Main) {
            onStatusUpdate?.invoke("同传就绪")
        }
        true
    }

    /**
     * 开始同传
     */
    fun start() {
        if (!isInitialized) {
            Log.e(TAG, "start: 同传未初始化")
            onError?.invoke("同传未初始化，请先调用 initialize()")
            return
        }
        if (isRunning) {
            Log.w(TAG, "start: 已经在运行中")
            return
        }

        isRunning = true
        lastRecognizedText = ""
        lastTranslateTime = 0L

        // 启动翻译消费协程
        translateJob = scope.launch(Dispatchers.IO) {
            Log.i(TAG, "translateJob: 翻译消费协程启动")
            for (text in translateChannel) {
                translateAndPlay(text)
            }
            Log.i(TAG, "translateJob: 翻译消费协程结束")
        }

        Log.i(TAG, "start: 同传开始 ✓")
        onStatusUpdate?.invoke("同传中...")
    }

    /**
     * 处理识别结果（从 VoiceEngine 回调）
     * 每次识别到文本都会调用（包括中间结果和最终结果）
     */
    fun onRecognitionResult(text: String, isFinal: Boolean) {
        if (!isRunning) return

        lastRecognizedText = text
        onRecognized?.invoke(text)

        val now = System.currentTimeMillis()
        val textLen = text.trim().length

        // 条件：文本足够长 + 距离上次翻译超过间隔
        if (textLen >= MIN_TEXT_LENGTH && now - lastTranslateTime > TRANSLATE_DELAY_MS) {
            lastTranslateTime = now
            val sent = translateChannel.trySend(text.trim())
            Log.d(TAG, "onRecognitionResult: 发送翻译请求 '$text' isFinal=$isFinal sent=${sent.isSuccess}")
        }
    }

    /**
     * 翻译并播放
     */
    private suspend fun translateAndPlay(englishText: String) {
        if (englishText.isBlank()) return

        Log.d(TAG, "translateAndPlay: '$englishText'")

        try {
            // MNN 翻译
            val translation = translateWithMnn(englishText)

            if (translation.isNullOrBlank()) {
                Log.w(TAG, "translateAndPlay: 翻译结果为空，跳过 TTS")
                return
            }

            Log.i(TAG, "translateAndPlay: '$englishText' → '$translation'")

            // 更新 UI（主线程）
            withContext(Dispatchers.Main) {
                onTranslated?.invoke(translation)
            }

            // TTS 播放（speak 内部已同步等待）
            Log.d(TAG, "translateAndPlay: 开始 TTS 播放")
            val ttsResult = ttsEngine.speak(translation)
            Log.d(TAG, "translateAndPlay: TTS 播放完成 result=$ttsResult")

        } catch (e: CancellationException) {
            throw e // 协程取消，不处理
        } catch (e: Exception) {
            Log.e(TAG, "translateAndPlay: ${e.message}", e)
            withContext(Dispatchers.Main) {
                onError?.invoke("翻译失败: ${e.message}")
            }
        }
    }

    /**
     * 调用 MNN 翻译（同步阻塞，在 IO 协程中执行）
     */
    private fun translateWithMnn(englishText: String): String? {
        val engine = aiEngine ?: run {
            Log.e(TAG, "translateWithMnn: AIEngine 未设置")
            return null
        }

        return try {
            val prompt = buildTranslatePrompt(englishText)
            Log.d(TAG, "translateWithMnn: 调用 syncGenerate")
            val result = engine.syncGenerate(prompt, maxTokens = 256)
            Log.d(TAG, "translateWithMnn: syncGenerate 返回 '$result'")
            result?.trim()?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.e(TAG, "translateWithMnn: ${e.message}", e)
            null
        }
    }

    /**
     * 英译中 prompt
     */
    private fun buildTranslatePrompt(englishText: String): String {
        return """你是一个专业的英译中翻译引擎。请将以下英文翻译成自然流畅的中文。

要求：
- 只输出翻译结果，不要解释
- 保持原文的语气和风格
- 使用自然的中文表达

英文：$englishText

中文翻译："""
    }

    /**
     * 停止同传
     */
    fun stop() {
        if (!isRunning) return
        isRunning = false

        translateJob?.cancel()
        translateJob = null
        translateChannel.cancel()

        // 停止 TTS 播放
        try {
            ttsEngine.stop()
        } catch (e: Exception) {
            Log.w(TAG, "stop: TTS stop 异常: ${e.message}")
        }

        Log.i(TAG, "stop: 同传停止")
        onStatusUpdate?.invoke("同传已停止")
    }

    /**
     * 释放资源
     */
    fun release() {
        stop()
        ttsEngine.release()
        aiEngine = null
        isInitialized = false
        scope.cancel()
        Log.i(TAG, "release: 同传资源已释放")
    }

    fun isRunning(): Boolean = isRunning
    fun isInitialized(): Boolean = isInitialized
}
