package com.cesia.input.engine.ai

import android.util.Log

/**
 * MNN LLM 本地 AI 推理引擎
 */
class MNNEngine {

    companion object {
        private const val TAG = "MNNEngine"

        init {
            try {
                // 先加载 APK 自带的 libc++_shared.so
                // Android 7.0+ 会从 nativeLibraryDir 搜索
                Log.i(TAG, "MNNEngine: loading c++_shared...")
                System.loadLibrary("c++_shared")
                Log.i(TAG, "MNNEngine: c++_shared loaded OK")

                // 按依赖顺序加载：子模块 → MNN → llm → bridge
                val libs = arrayOf(
                    "MNN_Vulkan", "MNN_CL", "MNNOpenCV", "MNNAudio", "MNN_Express",
                    "MNN", "llm", "mnn-llm-bridge"
                )
                for (lib in libs) {
                    Log.i(TAG, "MNNEngine: loading $lib...")
                    System.loadLibrary(lib)
                    Log.i(TAG, "MNNEngine: $lib loaded OK")
                }
                Log.i(TAG, "MNNEngine: all libraries loaded successfully")
            } catch (e: Throwable) {
                Log.e(TAG, "MNNEngine: Failed to load libraries", e)
            }
        }
    }

    interface Callback {
        fun onToken(token: String)
        fun onComplete()
        fun onError(error: String)
    }

    external fun nativePreloadLibcPlusPlus(libPath: String): Boolean
    external fun nativeInit(configPath: String): Boolean
    external fun nativeGenerate(prompt: String, maxTokens: Int): String
    external fun nativeGenerateStreaming(prompt: String, maxTokens: Int, callback: Callback)
    external fun nativeFree()
    external fun nativeIsLoaded(): Boolean
    external fun nativeGetLog(): String
    external fun nativeReset()
}
