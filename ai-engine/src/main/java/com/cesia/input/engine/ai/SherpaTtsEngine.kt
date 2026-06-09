package com.cesia.input.engine.ai

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * TTS 引擎封装
 *
 * 使用 Android 系统 TextToSpeech API（无需下载模型）
 * 支持中文/英文语音合成，依赖系统内置语音引擎（如 Google TTS、华为语音等）
 *
 * 用法：
 * 1. create(context) 初始化
 * 2. speak(text) 合成并播放（同步等待，最多 10s）
 * 3. release() 释放资源
 */
class SherpaTtsEngine {

    companion object {
        private const val TAG = "SherpaTtsEngine"
    }

    private var tts: TextToSpeech? = null
    private var isLoaded = false
    private var initLatch = CountDownLatch(1)

    /**
     * 初始化 TTS 引擎
     * @param context 应用 context
     * @return true 初始化成功
     */
    fun create(context: Context): Boolean {
        return try {
            initLatch = CountDownLatch(1)
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = tts?.setLanguage(Locale.CHINESE)
                    isLoaded = when (result) {
                        TextToSpeech.LANG_AVAILABLE,
                        TextToSpeech.LANG_COUNTRY_AVAILABLE,
                        TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> {
                            Log.i(TAG, "create: TTS 初始化成功 (中文)")
                            true
                        }
                        else -> {
                            // 中文不可用，尝试英文
                            val enResult = tts?.setLanguage(Locale.ENGLISH)
                            if (enResult == TextToSpeech.LANG_AVAILABLE ||
                                enResult == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
                                enResult == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE) {
                                Log.i(TAG, "create: TTS 初始化成功 (英文，中文不可用)")
                                true
                            } else {
                                Log.e(TAG, "create: TTS 语言不可用")
                                false
                            }
                        }
                    }
                } else {
                    Log.e(TAG, "create: TTS 初始化失败 status=$status")
                    isLoaded = false
                }
                initLatch.countDown()
            }

            // 等待初始化完成（最多 5s）
            initLatch.await(5, TimeUnit.SECONDS)
            isLoaded
        } catch (e: Exception) {
            Log.e(TAG, "create: TTS 初始化异常: ${e.message}", e)
            isLoaded = false
            false
        }
    }

    /**
     * 合成并播放文字（同步等待播放完成）
     * @param text 要播放的文字
     * @return true 播放成功
     */
    fun speak(text: String): Boolean {
        if (!isLoaded || tts == null) {
            Log.e(TAG, "speak: TTS 未加载")
            return false
        }
        if (text.isBlank()) {
            Log.w(TAG, "speak: 空文本，跳过")
            return true
        }

        return try {
            Log.d(TAG, "speak: '$text'")
            val latch = CountDownLatch(1)
            var success = true

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    latch.countDown()
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "speak: 播放错误")
                    success = false
                    latch.countDown()
                }
            })

            val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cesia_tts")
            if (result != TextToSpeech.SUCCESS) {
                Log.e(TAG, "speak: speak() 返回失败 result=$result")
                return false
            }

            // 等待播放完成（最多 10s）
            latch.await(10, TimeUnit.SECONDS)
            Log.d(TAG, "speak: 播放完成 success=$success")
            success
        } catch (e: Exception) {
            Log.e(TAG, "speak: 播放失败: ${e.message}", e)
            false
        }
    }

    fun isLoaded(): Boolean = isLoaded

    fun release() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "release: ${e.message}")
        }
        tts = null
        isLoaded = false
        Log.i(TAG, "release: TTS 已释放")
    }
}
