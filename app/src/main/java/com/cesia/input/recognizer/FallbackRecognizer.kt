package com.cesia.input.recognizer

import android.content.Context
import android.speech.SpeechRecognizer
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * 语音识别器（Android SpeechRecognizer）
 * 持续监听模式：识别完成后自动重启，保持始终在线
 */
class FallbackRecognizer(private val context: Context) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    sealed class Result {
        data class Success(val text: String, val confidence: Float = 1.0f) : Result()
        data class Partial(val text: String) : Result()
        data class Error(val message: String) : Result()
        data class Recognizing(val text: String) : Result()
        object NoMatch : Result()
    }

    private val _results = MutableSharedFlow<Result>(replay = 0, extraBufferCapacity = 1)
    val results = _results.asSharedFlow()

    /**
     * 是否在安静（无语音）时保持沉默，而不是报错
     */
    var suppressNoMatchError: Boolean = true

    fun init(): Boolean {
        return try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).also { recognizer ->
                recognizer.setRecognitionListener(object : android.speech.RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        // 准备就绪
                    }

                    override fun onBeginningOfSpeech() {}

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        // 说话结束，系统正在处理中
                        CoroutineScope(Dispatchers.Main).launch {
                            _results.emit(Result.Recognizing("正在识别..."))
                        }
                    }

                    override fun onError(error: Int) {
                        when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                                // 安静/无语音时不报错，保持静默
                                if (!suppressNoMatchError) {
                                    CoroutineScope(Dispatchers.Main).launch {
                                        _results.emit(Result.NoMatch)
                                    }
                                }
                                // 自动重启：保持持续监听
                                if (isListening) {
                                    restartListening()
                                }
                            }
                            SpeechRecognizer.ERROR_CLIENT -> {
                                // 客户端错误（通常是还在处理中），不报错
                                if (!suppressNoMatchError) {
                                    CoroutineScope(Dispatchers.Main).launch {
                                        _results.emit(Result.Recognizing("正在识别..."))
                                    }
                                }
                                // 延迟后自动重启
                                if (isListening) {
                                    CoroutineScope(Dispatchers.Main).launch {
                                        delay(500)
                                        restartListening()
                                    }
                                }
                            }
                            SpeechRecognizer.ERROR_NETWORK -> {
                                CoroutineScope(Dispatchers.Main).launch {
                                    _results.emit(Result.Error("网络错误，请检查网络"))
                                }
                                if (isListening) {
                                    CoroutineScope(Dispatchers.Main).launch {
                                        delay(1000)
                                        restartListening()
                                    }
                                }
                            }
                            SpeechRecognizer.ERROR_AUDIO -> {
                                CoroutineScope(Dispatchers.Main).launch {
                                    _results.emit(Result.Error("音频错误"))
                                }
                                if (isListening) {
                                    CoroutineScope(Dispatchers.Main).launch {
                                        delay(500)
                                        restartListening()
                                    }
                                }
                            }
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                                CoroutineScope(Dispatchers.Main).launch {
                                    _results.emit(Result.Error("需要录音权限"))
                                }
                            }
                            else -> {
                                // 其他错误
                                val msg = "识别错误"
                                CoroutineScope(Dispatchers.Main).launch {
                                    _results.emit(Result.Error(msg))
                                }
                                if (isListening) {
                                    CoroutineScope(Dispatchers.Main).launch {
                                        delay(500)
                                        restartListening()
                                    }
                                }
                            }
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val best = matches[0]
                            val scores = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                            val confidence = scores?.firstOrNull() ?: 1.0f
                            CoroutineScope(Dispatchers.Main).launch {
                                _results.emit(Result.Success(best, confidence))
                            }
                            // 识别成功：如果是持续监听模式，自动重启
                            if (isListening) {
                                restartListening()
                            }
                        } else {
                            if (!suppressNoMatchError) {
                                CoroutineScope(Dispatchers.Main).launch {
                                    _results.emit(Result.NoMatch)
                                }
                            }
                            if (isListening) {
                                restartListening()
                            }
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            CoroutineScope(Dispatchers.Main).launch {
                                _results.emit(Result.Partial(matches[0]))
                            }
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
            true
        } catch (e: Exception) {
            Log.e("FallbackRecognizer", "初始化失败", e)
            false
        }
    }

    fun startListening(): Boolean {
        val recognizer = speechRecognizer ?: return false
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return false

        isListening = true
        return try {
            val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            recognizer.startListening(intent)
            true
        } catch (e: Exception) {
            Log.e("FallbackRecognizer", "启动识别失败", e)
            false
        }
    }

    /**
     * 重新启动监听（保持 isListening 状态）
     */
    private fun restartListening() {
        speechRecognizer?.destroy()
        speechRecognizer = null

        if (isListening && init()) {
            startListening()
        }
    }

    fun stopListening() {
        isListening = false
        speechRecognizer?.stopListening()
    }

    fun destroy() {
        isListening = false
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }
}
