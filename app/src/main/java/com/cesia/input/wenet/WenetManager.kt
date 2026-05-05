package com.cesia.input.wenet

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.AsyncTask
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class WenetManager(private val context: Context) {
    
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    
    // WeNet模型路径
    private val modelFile = File(context.filesDir, "wenet_model.bin")
    private val unitFile = File(context.filesDir, "wenet_units.txt")
    
    private val client = OkHttpClient()
    private val modelUrl = "https://github.com/wenet-e2e/wenet/releases/download/v2.0.1/wenet_model.bin"
    private val unitUrl = "https://github.com/wenet-e2e/wenet/releases/download/v2.0.1/wenet_units.txt"
    
    interface DownloadCallback {
        fun onProgress(progress: Int)
        fun onComplete()
        fun onError(error: String)
    }
    
    fun isModelDownloaded(): Boolean {
        return modelFile.exists() && unitFile.exists()
    }
    
    fun downloadModel(callback: DownloadCallback) {
        AsyncTask.execute {
            try {
                // 下载模型
                callback.onProgress(0)
                downloadFile(modelUrl, modelFile, callback, 0, 50)
                
                // 下载units
                callback.onProgress(50)
                downloadFile(unitUrl, unitFile, callback, 50, 50)
                
                callback.onProgress(100)
                callback.onComplete()
                
                // 初始化WeNet
                initWenet(modelFile.absolutePath, unitFile.absolutePath)
                
            } catch (e: Exception) {
                callback.onError(e.message ?: "下载失败")
            }
        }
    }
    
    private fun downloadFile(url: String, outputFile: File, callback: DownloadCallback, progressOffset: Int, progressRange: Int) {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw IOException("HTTP ${response.code}")
        }
        
        val body = response.body ?: throw IOException("Empty response")
        val totalBytes = body.contentLength()
        var downloadedBytes = 0L
        
        FileOutputStream(outputFile).use { fos ->
            body.byteStream().use { input ->
                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    fos.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    if (totalBytes > 0) {
                        val progress = progressOffset + ((downloadedBytes.toFloat() / totalBytes) * progressRange).toInt()
                        callback.onProgress(progress.coerceIn(0, 100))
                    }
                }
            }
        }
    }
    
    fun startRecording() {
        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate, channelConfig, audioFormat
        )
        
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate, channelConfig, audioFormat,
            minBufferSize
        )
        
        audioRecord?.startRecording()
        isRecording = true
        
        // 在后台线程录音
        Thread {
            val buffer = ByteArray(minBufferSize)
            val outputFile = File(context.cacheDir, "recording.pcm")
            FileOutputStream(outputFile).use { fos ->
                while (isRecording) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (read > 0) {
                        fos.write(buffer, 0, read)
                    }
                }
            }
            
            // 识别录音
            recognizeAudio(outputFile.absolutePath)
        }.start()
    }
    
    fun stopRecording(): String {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        
        return ""
    }
    
    private external fun initWenet(modelPath: String, unitPath: String)
    private external fun recognizeAudio(pcmPath: String): String
    
    companion object {
        init {
            System.loadLibrary("wenet-jni")
        }
    }
}
