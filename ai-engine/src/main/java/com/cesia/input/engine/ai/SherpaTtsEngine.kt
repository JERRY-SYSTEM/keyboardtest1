package com.cesia.input.engine.ai

import android.content.res.AssetManager
import android.util.Log
import com.k2fsa.sherpa.onnx.*
import java.io.File

/**
 * Sherpa-onnx TTS 引擎封装
 *
 * 使用 OfflineTts 将文字转换为语音并直接播放
 * 支持模型：Vits、Matcha、Kokoro、ZipVoice 等
 *
 * 用法：
 * 1. create() 加载模型
 * 2. speak(text) 合成并播放
 * 3. release() 释放资源
 */
class SherpaTtsEngine {

    companion object {
        private const val TAG = "SherpaTtsEngine"
    }

    private var tts: OfflineTts? = null
    private var isLoaded = false

    /**
     * 加载 TTS 模型
     * @param modelDir 模型目录，包含 model.onnx（或 model.int8.onnx）+ tokens.txt
     * @param voiceId 说话人 ID（多说话人模型用，默认 0）
     * @param speed 语速（1.0 正常，<1.0 更慢，>1.0 更快）
     * @param numThreads 线程数
     * @return true 加载成功
     */
    fun create(
        modelDir: String,
        voiceId: Int = 0,
        speed: Float = 1.0f,
        numThreads: Int = 2
    ): Boolean {
        return try {
            release() // 先释放旧的

            val modelFile = findModelFile(modelDir)
            if (modelFile == null) {
                Log.e(TAG, "create: 未找到模型文件 in $modelDir")
                return false
            }

            val tokensFile = "$modelDir/tokens.txt"

            val modelConfig = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = modelFile.absolutePath,
                    lexicon = "",
                    tokens = tokensFile,
                    dataDir = "",
                    dictDir = "",
                    noiseScale = 0.667f,
                    noiseScaleW = 0.8f,
                    lengthScale = 1.0f / speed
                ),
                matcha = OfflineTtsMatchaModelConfig(),
                kokoro = OfflineTtsKokoroModelConfig(),
                numThreads = numThreads,
                debug = false,
                provider = "cpu"
            )

            val config = OfflineTtsConfig(
                model = modelConfig,
                ruleFsts = "",
                ruleFars = "",
                maxNumSentences = 1,
                silenceScale = 0.2f
            )

            // 使用 AssetManager 方式创建（传 null 表示从文件路径加载）
            tts = OfflineTts(null as? AssetManager, config)
            isLoaded = true
            Log.i(TAG, "create: TTS 模型加载成功 model=${modelFile.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "create: TTS 模型加载失败: ${e.message}", e)
            isLoaded = false
            false
        }
    }

    /**
     * 合成并播放文字
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
            val audio = tts!!.generate(text)
            if (audio.samples.isEmpty()) {
                Log.e(TAG, "speak: 生成的音频为空")
                return false
            }

            // 用 AudioTrack 播放
            playAudio(audio.samples, audio.sampleRate)
            Log.d(TAG, "speak: 播放完成 samples=${audio.samples.size}, rate=${audio.sampleRate}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "speak: 播放失败: ${e.message}", e)
            false
        }
    }

    /**
     * 用 AudioTrack 播放 PCM 音频数据
     */
    private fun playAudio(samples: FloatArray, sampleRate: Int) {
        try {
            val minBufferSize = android.media.AudioTrack.getMinBufferSize(
                sampleRate,
                android.media.AudioFormat.CHANNEL_OUT_MONO,
                android.media.AudioFormat.ENCODING_PCM_FLOAT
            )

            val audioTrack = android.media.AudioTrack.Builder()
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    android.media.AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(android.media.AudioFormat.ENCODING_PCM_FLOAT)
                        .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBufferSize.coerceAtLeast(samples.size * 4))
                .setTransferMode(android.media.AudioTrack.MODE_STREAM)
                .build()

            audioTrack.play()
            audioTrack.write(samples, 0, samples.size, android.media.AudioTrack.WRITE_BLOCKING)
            audioTrack.stop()
            audioTrack.release()
        } catch (e: Exception) {
            Log.e(TAG, "playAudio: ${e.message}", e)
        }
    }

    /**
     * 查找模型文件（支持 .onnx 和 .int8.onnx）
     */
    private fun findModelFile(modelDir: String): File? {
        val dir = File(modelDir)
        if (!dir.exists() || !dir.isDirectory) return null

        // 优先 int8 量化模型（更小更快）
        val int8Model = File(dir, "model.int8.onnx")
        if (int8Model.exists()) return int8Model

        // 标准模型
        val onnxModel = File(dir, "model.onnx")
        if (onnxModel.exists()) return onnxModel

        // 任意 .onnx 文件
        return dir.listFiles()?.firstOrNull { it.name.endsWith(".onnx") }
    }

    fun isLoaded(): Boolean = isLoaded

    fun release() {
        try {
            tts?.release()
        } catch (e: Exception) {
            Log.w(TAG, "release: ${e.message}")
        }
        tts = null
        isLoaded = false
        Log.i(TAG, "release: TTS 已释放")
    }
}
