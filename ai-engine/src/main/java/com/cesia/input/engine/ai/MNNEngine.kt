package com.cesia.input.engine.ai

import android.util.Log

/**
 * MNN LLM 本地 AI 推理引擎
 *
 * 使用前需:
 * 1. 下载 MNN 模型到本地目录（config.json + llm.mnn + llm.mnn.weight + tokenizer.txt）
 * 2. 调用 init() 加载模型
 * 3. 调用 generate() 生成文本
 * 4. 调用 release() 释放资源
 *
 * JNI 对应: ai-engine/src/main/cpp/mnn-llm.cpp
 */
class MNNEngine {

    companion object {
        private const val TAG = "MNNEngine"

        init {
            try {
                System.loadLibrary("mnn-llm-bridge")
                Log.i(TAG, "mnn-llm-bridge loaded")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load mnn-llm-bridge", e)
            }
        }
    }

    /**
     * 流式生成回调接口
     */
    interface Callback {
        fun onToken(token: String)
        fun onComplete()
        fun onError(error: String)
    }

    /**
     * 初始化 MNN LLM 模型
     * @param configPath config.json 的绝对路径
     * @return 是否成功
     */
    external fun nativeInit(configPath: String): Boolean

    /**
     * 生成文本（同步 — 会阻塞直到完成）
     * @param prompt 输入 prompt
     * @param maxTokens 最大生成 token 数
     * @return 生成的文本
     */
    external fun nativeGenerate(prompt: String, maxTokens: Int): String

    /**
     * 流式生成文本（回调方式）
     * @param prompt 输入 prompt
     * @param maxTokens 最大生成 token 数
     * @param callback 回调接口
     */
    external fun nativeGenerateStreaming(prompt: String, maxTokens: Int, callback: Callback)

    /**
     * 释放模型资源
     */
    external fun nativeFree()

    /**
     * 检查模型是否已加载
     */
    external fun nativeIsLoaded(): Boolean

    /**
     * 获取 LLM 日志
     */
    external fun nativeGetLog(): String

    /**
     * 重置对话历史
     */
    external fun nativeReset()
}
