package com.cesia.input.engine

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * 轻量级拼音输入引擎
 * 内置常用字拼音字典（~8000字）+ 常用词组字典（~700条）
 * 支持全拼输入、词组匹配和候选词选择
 */
class PinyinEngine(context: Context) {

    private val pinyinMap = mutableMapOf<String, String>()
    private val phraseMap = mutableMapOf<String, String>()
    private var currentPinyin = StringBuilder()
    private var candidates = listOf<String>()
    private var candidatePages = listOf<List<String>>()
    private var currentPage = 0

    init {
        loadDictionary(context)
        loadPhrases(context)
    }

    private fun loadDictionary(context: Context) {
        try {
            val jsonStr = context.assets.open("pinyin_dict.json").bufferedReader().readText()
            val json = JSONObject(jsonStr)
            for (key in json.keys()) {
                pinyinMap[key] = json.getString(key)
            }
            Log.d("PinyinEngine", "拼音字典加载完成: ${pinyinMap.size} 个拼音条目")
        } catch (e: Exception) {
            Log.e("PinyinEngine", "拼音字典加载失败", e)
        }
    }

    private fun loadPhrases(context: Context) {
        try {
            val jsonStr = context.assets.open("pinyin_phrases.json").bufferedReader().readText()
            val json = JSONObject(jsonStr)
            for (key in json.keys()) {
                phraseMap[key] = json.getString(key)
            }
            Log.d("PinyinEngine", "词组字典加载完成: ${phraseMap.size} 个词组条目")
        } catch (e: Exception) {
            Log.e("PinyinEngine", "词组字典加载失败", e)
        }
    }

    /**
     * 输入一个字母，返回当前拼音串
     */
    fun inputLetter(c: Char): String {
        if (c in 'a'..'z') {
            currentPinyin.append(c)
            updateCandidates()
        }
        return currentPinyin.toString()
    }

    /**
     * 退格一个字母
     */
    fun backspace(): String {
        if (currentPinyin.isNotEmpty()) {
            currentPinyin.deleteCharAt(currentPinyin.length - 1)
            updateCandidates()
        }
        return currentPinyin.toString()
    }

    /**
     * 清空当前拼音
     */
    fun clear() {
        currentPinyin.clear()
        candidates = emptyList()
        candidatePages = emptyList()
        currentPage = 0
    }

    /**
     * 获取当前拼音串
     */
    fun getCurrentPinyin(): String = currentPinyin.toString()

    /**
     * 获取当前页候选词
     */
    fun getCandidates(): List<String> {
        if (candidatePages.isEmpty()) return emptyList()
        return candidatePages[currentPage]
    }

    /**
     * 获取总页数
     */
    fun getPageCount(): Int = candidatePages.size

    /**
     * 获取当前页码
     */
    fun getCurrentPage(): Int = currentPage

    /**
     * 是否有候选词
     */
    fun hasCandidates(): Boolean = candidates.isNotEmpty()

    /**
     * 是否正在输入拼音
     */
    fun isComposing(): Boolean = currentPinyin.isNotEmpty()

    /**
     * 翻到下一页候选词
     */
    fun nextPage(): List<String> {
        if (currentPage < candidatePages.size - 1) {
            currentPage++
        }
        return getCandidates()
    }

    /**
     * 翻到上一页候选词
     */
    fun prevPage(): List<String> {
        if (currentPage > 0) {
            currentPage--
        }
        return getCandidates()
    }

    /**
     * 选择候选词，返回选中的词/字并清空拼音
     * @param index 全局索引（页码*每页数量+页内索引）
     */
    fun selectCandidate(index: Int): String {
        if (index < 0 || index >= candidates.size) return ""
        val selected = candidates[index]
        clear()
        return selected
    }

    private fun updateCandidates() {
        val pinyin = currentPinyin.toString()
        if (pinyin.isEmpty()) {
            candidates = emptyList()
            candidatePages = emptyList()
            currentPage = 0
            return
        }

        val allCandidates = mutableListOf<String>()

        // 1. 优先匹配词组（精确匹配）
        val exactPhrase = phraseMap[pinyin]
        if (exactPhrase != null) {
            allCandidates.add(exactPhrase)
        }

        // 2. 匹配单字（精确匹配）
        val exactChars = pinyinMap[pinyin]
        if (exactChars != null) {
            // 将字符串拆分为单个字符，最多取前5个
            exactChars.take(5).forEach { allCandidates.add(it.toString()) }
        }

        // 3. 前缀匹配词组
        for ((key, value) in phraseMap) {
            if (key.startsWith(pinyin) && key != pinyin) {
                allCandidates.add(value)
            }
        }

        // 4. 前缀匹配单字
        if (allCandidates.size < 10) {
            for ((key, value) in pinyinMap) {
                if (key.startsWith(pinyin) && key != pinyin) {
                    value.forEach { allCandidates.add(it.toString()) }
                    if (allCandidates.size >= 20) break
                }
            }
        }

        // 去重并限制数量
        candidates = allCandidates.distinct().take(30)

        // 分页，每页5个
        if (candidates.isEmpty()) {
            candidatePages = emptyList()
        } else {
            candidatePages = candidates.chunked(5)
        }
        currentPage = 0
    }
}
