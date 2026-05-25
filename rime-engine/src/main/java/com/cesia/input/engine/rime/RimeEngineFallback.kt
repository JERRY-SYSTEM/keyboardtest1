package com.cesia.input.engine.rime

import android.content.Context
import android.util.Log

/**
 * 纯 Kotlin 拼音输入引擎（Fallback）
 * 当 Rime native 库不可用时提供基本的中文拼音输入功能
 * 词库来源：pinyin_dict.json（assets 内嵌）
 *
 * 词库格式：{"su":"素苏速俗诉宿酥塑...", "du":"度独毒..."}
 * 每个拼音对应一串连续汉字，按单字切分为候选词
 * 支持多音节拼音分词匹配：sudu → su+du → 速度
 */
class RimeEngineFallback(private val context: Context) {

    companion object {
        private const val TAG = "RimeFallback"
    }

    private var isReady = false
    // 拼音 → 单字候选词列表（按频率排序）
    private val pinyinMap = LinkedHashMap<String, List<String>>()
    // 当前状态
    private var currentPinyin = ""
    private var currentPage = 0
    private val pageSize = 5

    val isInitialized: Boolean get() = isReady
    val isComposing: Boolean get() = currentPinyin.isNotEmpty()
    val composingText: String get() = currentPinyin

    val candidates: List<String>
        get() {
            if (currentPinyin.isEmpty()) return emptyList()
            val words = findCandidates(currentPinyin) ?: return emptyList()
            val from = currentPage * pageSize
            if (from >= words.size) return emptyList()
            val to = minOf(from + pageSize, words.size)
            return words.subList(from, to)
        }

    val hasCandidates: Boolean get() = candidates.isNotEmpty()

    val pageCount: Int
        get() {
            val words = findCandidates(currentPinyin) ?: return 0
            return (words.size + pageSize - 1) / pageSize
        }

    val currentPageIdx: Int get() = currentPage

    fun initialize(): Boolean {
        if (isReady) return true
        try {
            val json = context.assets.open("pinyin_dict.json")
                .bufferedReader().use { it.readText() }
            val obj = org.json.JSONObject(json)
            var totalEntries = 0
            for (key in obj.keys()) {
                val value = obj.getString(key)
                // 按单字切分，每个汉字是一个候选词
                val chars = value.map { it.toString() }
                pinyinMap[key] = chars
                totalEntries += chars.size
            }
            isReady = true
            Log.i(TAG, "拼音引擎初始化成功: ${pinyinMap.size} 个拼音条目, $totalEntries 个候选字")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "拼音引擎初始化失败", e)
            return false
        }
    }

    /**
     * 查找候选词
     * 先查完整拼音（如 "su"），如果查不到则尝试分词匹配（如 "sudu" → "su"+"du"）
     */
    private fun findCandidates(pinyin: String): List<String>? {
        // 1. 直接匹配完整拼音
        pinyinMap[pinyin]?.let { return it }

        // 2. 分词匹配：尝试将多音节拼音拆分为多个单音节
        //    例如 "sudu" → ["su", "du"]，然后取每个音节的第1个候选字组合
        val segments = splitPinyin(pinyin)
        if (segments.size >= 2) {
            // 取每个音节的第1个候选字，组合成词
            val firstChars = mutableListOf<String>()
            for (seg in segments) {
                val chars = pinyinMap[seg] ?: return null
                firstChars.add(chars[0])
            }
            // 返回组合词 + 各音节的首字作为候选
            val combined = firstChars.joinToString("")
            val candidates = mutableListOf(combined)
            // 也加入各音节的首字作为单字候选
            for (seg in segments) {
                val chars = pinyinMap[seg]!!
                for (c in chars.take(5)) {
                    if (c !in candidates) candidates.add(c)
                }
            }
            return candidates
        }

        return null
    }

    /**
     * 简单的拼音分词：贪心匹配最长拼音
     * 例如 "sudu" → ["su", "du"]
     */
    private fun splitPinyin(pinyin: String): List<String> {
        val result = mutableListOf<String>()
        var i = 0
        while (i < pinyin.length) {
            // 贪心：尝试匹配最长的拼音（最多6个字母，如 "zhuang"）
            var matched = false
            for (len in minOf(6, pinyin.length - i) downTo 1) {
                val sub = pinyin.substring(i, i + len)
                if (pinyinMap.containsKey(sub)) {
                    result.add(sub)
                    i += len
                    matched = true
                    break
                }
            }
            if (!matched) {
                // 无法匹配，跳过这个字符
                i++
            }
        }
        return result
    }

    fun inputLetter(c: Char): String {
        if (!isReady) return ""
        currentPinyin += c
        currentPage = 0
        Log.d(TAG, "inputLetter: $currentPinyin")
        return currentPinyin
    }

    fun backspace(): String {
        if (!isReady) return ""
        if (currentPinyin.isNotEmpty()) {
            currentPinyin = currentPinyin.dropLast(1)
        }
        currentPage = 0
        Log.d(TAG, "backspace: $currentPinyin")
        return currentPinyin
    }

    fun getCurrentPinyin(): String = currentPinyin

    fun selectCandidate(index: Int): String {
        if (!isReady) return ""
        val cands = candidates
        if (index < 0 || index >= cands.size) return ""
        return cands[index]
    }

    fun clear() {
        currentPinyin = ""
        currentPage = 0
    }

    fun nextPage(): List<String> {
        if (currentPage < pageCount - 1) currentPage++
        return candidates
    }

    fun prevPage(): List<String> {
        if (currentPage > 0) currentPage--
        return candidates
    }
}
