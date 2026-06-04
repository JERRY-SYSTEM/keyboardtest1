package com.cesia.input.model

/**
 * 模型信息
 */
data class ModelInfo(
    val id: String,                  // 唯一标识，如 "whisper-small", "qwen-2b"
    val name: String,                // 显示名，如 "Whisper Small"
    val description: String,         // 描述
    val downloadUrl: String,         // HuggingFace 下载链接
    val fileName: String,            // 本地文件名
    val sizeBytes: Long,             // 文件大小（字节）
    val sha256: String? = null,      // 校验和（可选）
    val type: ModelType              // 语音 or AI
) {
    enum class ModelType { VOICE, AI }

    /** 简单安装 vs 高级安装 */
    enum class Tier { BASIC, PREMIUM }

    val tier: Tier
        get() = when (id) {
            "whisper-small", "qwen-0.8b" -> Tier.BASIC
            "whisper-large-turbo", "qwen-2b" -> Tier.PREMIUM
            else -> Tier.BASIC
        }
}

/**
 * 所有可用模型定义
 */
object ModelRegistry {

    const val KB = 1024L
    const val MB = KB * 1024
    const val GB = MB * 1024

    val ALL_MODELS = listOf(
        // === 语音识别模型 ===
        ModelInfo(
            id = "whisper-small",
            name = "Whisper Small",
            description = "语音识别基础模型（~400MB），准确率高，适合日常使用",
            downloadUrl = "https://hf-mirror.com/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_0.bin",
            fileName = "ggml-small-q5_0.bin",
            sizeBytes = 466L * MB,
            type = ModelInfo.ModelType.VOICE
        ),
        ModelInfo(
            id = "whisper-large-turbo",
            name = "Whisper Large V3 Turbo",
            description = "语音识别旗舰模型（~800MB），最高精度，适合专业场景",
            downloadUrl = "https://hf-mirror.com/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-turbo-q5_0.bin",
            fileName = "ggml-large-v3-turbo-q5_0.bin",
            sizeBytes = 809L * MB,
            type = ModelInfo.ModelType.VOICE
        ),

        // === AI 模型 ===
        ModelInfo(
            id = "qwen-0.8b",
            name = "Qwen 3.5 0.8B",
            description = "AI 润色轻量模型（~560MB），极速响应，省资源",
            downloadUrl = "https://hf-mirror.com/bartowski/Qwen3.5-0.8B-Instruct-GGUF/resolve/main/Qwen3.5-0.8B-Instruct-Q4_K_M.gguf",
            fileName = "Qwen3.5-0.8B-Instruct-Q4_K_M.gguf",
            sizeBytes = 560L * MB,
            type = ModelInfo.ModelType.AI
        ),
        ModelInfo(
            id = "qwen-2b",
            name = "Qwen 3.5 2B",
            description = "AI 润色标准模型（~1.4GB），更好的润色质量",
            downloadUrl = "https://hf-mirror.com/bartowski/Qwen3.5-2B-Instruct-GGUF/resolve/main/Qwen3.5-2B-Instruct-Q4_K_M.gguf",
            fileName = "Qwen3.5-2B-Instruct-Q4_K_M.gguf",
            sizeBytes = 1400L * MB,
            type = ModelInfo.ModelType.AI
        )
    )

    fun getById(id: String): ModelInfo? = ALL_MODELS.find { it.id == id }

    // ==================== 桥梁插件（native-bridge.so）====================

    /**
     * 桥梁插件信息
     * 包含 whisper.cpp 的 JNI 实现，随模型一起下载
     *
     * 下载地址说明：
     * - 可以从 GitHub Release 下载预编译版本
     * - 也可以从 hf-mirror 下载
     * - 文件名格式：libnative-bridge-<abi>.so
     */
    object Bridge {
        const val ID = "native-bridge"
        const val FILE_NAME = "libnative-bridge.so"
        const val DISPLAY_NAME = "语音识别引擎（桥梁）"
        const val DESCRIPTION = "本地语音识别和 AI 润色的 native 引擎，必须下载后才能使用本地模型"

        // 预编译桥梁的下载 URL（按 ABI 区分）
        // 实际使用时替换为真实的下载地址
        const val DOWNLOAD_URL_ARM64 = "https://github.com/harviex/cesia-input-method/releases/download/bridge/libnative-bridge-arm64-v8a.so"

        // 文件大小（约 2-5MB，取决于编译选项）
        const val SIZE_BYTES = 5L * MB

        /**
         * 获取当前 ABI 对应的下载 URL
         */
        fun getDownloadUrl(): String {
            val abi = System.getProperty("os.arch") ?: "arm64"
            return when {
                abi.contains("aarch64") || abi.contains("arm64") -> DOWNLOAD_URL_ARM64
                else -> DOWNLOAD_URL_ARM64 // 默认 arm64
            }
        }

        /**
         * 获取当前 ABI 对应的文件名
         */
        fun getFileName(): String = FILE_NAME

        /**
         * 获取桥梁在设备上的安装路径
         * 返回 null 表示不支持当前平台
         */
        fun getInstallDir(context: android.content.Context): java.io.File? {
            // 放到 app 的 nativeLibraryDir 下
            val nativeDir = context.applicationInfo.nativeLibraryDir ?: return null
            return java.io.File(nativeDir)
        }

        /**
         * 检查桥梁是否已安装
         */
        fun isInstalled(context: android.content.Context): Boolean {
            val dir = getInstallDir(context) ?: return false
            return java.io.File(dir, FILE_NAME).exists()
        }

        /**
         * 尝试加载桥梁，返回是否成功
         */
        fun tryLoad(): Boolean {
            return try {
                System.loadLibrary("native-bridge")
                true
            } catch (e: UnsatisfiedLinkError) {
                false
            }
        }
    }
}
