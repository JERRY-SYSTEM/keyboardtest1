package com.cesia.input.engine.rime

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Rime 输入引擎实现
 * 当前为 stub 实现（纯 Kotlin），后续替换为真实 librime JNI 调用
 */
class RimeEngine(private val context: Context) : InputEngine {

    companion object {
        private const val TAG = "RimeEngine"
    }

    private var session: RimeSession? = null
    private val prefs = context.getSharedPreferences("cesia_rime", Context.MODE_PRIVATE)

    override val name: String = "Rime"

    override var isInitialized: Boolean = false
        private set

    override val isAvailable: Boolean
        get() = isInitialized && (RimeJni.isAvailable() || fallback.isInitialized)

    // --- Fallback 引擎 ---
    private val fallback = RimeEngineFallback(context)

    // --- 当前会话代理 ---

    override val isComposing: Boolean
        get() = if (RimeJni.isAvailable()) session?.hasComposing() ?: false else fallback.isComposing

    override val composingText: String
        get() = if (RimeJni.isAvailable()) session?.composingText ?: "" else fallback.composingText

    override val candidates: List<String>
        get() = if (RimeJni.isAvailable()) session?.candidates ?: emptyList() else fallback.candidates

    override val hasCandidates: Boolean
        get() = if (RimeJni.isAvailable()) session?.hasCandidates() ?: false else fallback.hasCandidates

    override val pageCount: Int
        get() = if (RimeJni.isAvailable()) session?.pageCount ?: 0 else fallback.pageCount

    override val currentPage: Int
        get() = if (RimeJni.isAvailable()) session?.currentPage ?: 0 else fallback.currentPageIdx

    // --- 生命周期 ---

    override fun initialize(): Boolean {
        if (isInitialized) return fallback.initialize()
        // 先将 APK assets 中的 rime 配置文件解压到 filesDir/rime（缺失才解压）
        copyRimeAssetsIfNeeded()
        // 诊断：检查关键文件
        val rimeDir = File(context.filesDir, "rime")
        Log.i(TAG, "Rime 资产目录: ${rimeDir.absolutePath}")
        Log.i(TAG, "default.yaml 存在=${File(rimeDir, "default.yaml").exists()}")
        Log.i(TAG, "pinyin.schema.yaml 存在=${File(rimeDir, "pinyin.schema.yaml").exists()}")
        Log.i(TAG, "pinyin.dict.yaml 存在=${File(rimeDir, "pinyin.dict.yaml").exists()}")
        val nativeOk = RimeJni.initialize(context)
        // 无论 native 是否成功，都初始化 fallback
        val fallbackOk = fallback.initialize()
        isInitialized = nativeOk || fallbackOk
        if (nativeOk) {
            Log.i(TAG, "Rime native 引擎初始化成功")
        }
        if (fallbackOk) {
            Log.i(TAG, "Rime fallback 引擎初始化成功")
        }
        if (!nativeOk) {
            Log.w(TAG, "Rime native 引擎初始化失败: ${RimeJni.unavailableMessage()} — 使用 fallback 引擎")
        }
        return isInitialized
    }

    /**
     * 将 assets/rime/ 下的配置文件解压到 filesDir/rime/
     * 仅在首次（或文件缺失）时执行
     */
    private fun copyRimeAssetsIfNeeded() {
        val rimeDir = File(context.filesDir, "rime")
        rimeDir.mkdirs()
        try {
            context.assets.list("rime")?.forEach { fileName ->
                val outFile = File(rimeDir, fileName)
                // 只覆盖不存在的文件，不覆盖用户已下载的词库
                if (!outFile.exists()) {
                    context.assets.open("rime/$fileName").use { input ->
                        outFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d(TAG, "解压 Rime 资产: $fileName -> ${outFile.absolutePath} (${outFile.length()} bytes)")
                }
            }
            prefs.edit().putBoolean("rime_assets_copied", true).apply()
        } catch (e: Exception) {
            Log.e(TAG, "解压 Rime 资产失败", e)
        }
    }

    override fun shutdown() {
        RimeJni.shutdown()
        session?.let {
            try {
                RimeJni.destroySession(it)
            } catch (_: Exception) {}
        }
        session = null
        isInitialized = false
    }

    /**
     * 热重载：关闭并重新初始化（词库下载完后调用）
     */
    fun reload(): Boolean {
        Log.i(TAG, "Rime 引擎热重载...")
        shutdown()
        return initialize()
    }

    // --- 会话管理 ---

    override fun createSession(): RimeSession {
        val s = RimeJni.createSession()
        session = s
        return s
    }

    override fun destroySession(session: RimeSession) {
        RimeJni.destroySession(session)
        if (this.session?.id == session.id) {
            this.session = null
        }
    }

    // --- 输入处理 ---

    override fun processKey(key: String): Boolean {
        return if (RimeJni.isAvailable()) {
            val s = session ?: createSession()
            s.processKey(key)
        } else {
            // Fallback: 处理退格
            if (key == "BackSpace") {
                fallback.backspace()
                true
            } else if (key.length == 1) {
                fallback.inputLetter(key[0])
                true
            } else false
        }
    }

    override fun processKey(c: Char): Boolean {
        return if (RimeJni.isAvailable()) {
            processKey(c.toString())
        } else {
            fallback.inputLetter(c)
            true
        }
    }

    override fun processKeyCode(keyCode: Int): Boolean {
        return if (RimeJni.isAvailable()) {
            val s = session ?: createSession()
            s.processKeyCode(keyCode)
        } else {
            when (keyCode) {
                -5, android.view.KeyEvent.KEYCODE_DEL -> {
                    fallback.backspace()
                    true
                }
                else -> false
            }
        }
    }

    override fun selectCandidate(index: Int): String {
        return if (RimeJni.isAvailable()) {
            val s = session ?: return ""
            s.selectCandidate(index)
        } else {
            fallback.selectCandidate(index)
        }
    }

    override fun commit(): String {
        return if (RimeJni.isAvailable()) {
            val s = session ?: return ""
            s.commit()
        } else {
            // Fallback: 提交当前拼音对应的第一个候选词
            val cands = candidates
            clear()
            cands.firstOrNull() ?: ""
        }
    }

    override fun clear() {
        if (RimeJni.isAvailable()) session?.clear() else fallback.clear()
    }

    override fun nextPage(): List<String> {
        if (RimeJni.isAvailable()) session?.nextPage()
        else fallback.nextPage()
        return candidates
    }

    override fun prevPage(): List<String> {
        if (RimeJni.isAvailable()) session?.prevPage()
        else fallback.prevPage()
        return candidates
    }

    // --- PinyinEngine 兼容方法 ---

    fun inputLetter(c: Char): String {
        processKey(c)
        return getCurrentPinyin()
    }

    fun backspace(): String {
        processKey("BackSpace")
        return getCurrentPinyin()
    }

    fun getCurrentPinyin(): String = composingText
}
