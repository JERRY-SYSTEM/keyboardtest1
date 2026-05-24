package com.cesia.input.engine

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Rime 词库管理器
 * 从 GitHub 下载 rime-ice 词库，保存到 filesDir/rime/
 * 
 * 词库来源：rime-ice (iDvel/rime-ice)，GPL-3.0 许可证
 * 用户需自行在设置页点击下载，不随 APK 分发
 */
class PinyinDictManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("cesia_dict", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "RimeDictManager"
        const val PREF_DICT_VERSION = "dict_version"
        const val PREF_DICT_SOURCE = "dict_source"
        const val PREF_LAST_SYNC = "last_sync"
        const val PREF_DICT_DOWNLOADED = "dict_downloaded"

        // rime-ice 词库下载源 (GPL-3.0)
        // 使用 GitHub Release 的 cn_dicts.zip（比 raw 路径更稳定）
        const val DICT_ZIP_URL = "https://github.com/iDvel/rime-ice/releases/download/nightly/cn_dicts.zip"

        // 本地保存路径：filesDir/rime/
        const val LOCAL_DICT_FILE = "pinyin.dict.yaml"
        const val LOCAL_BASE_FILE = "base.dict.yaml"
        const val LOCAL_8105_FILE = "8105.dict.yaml"
        // 向后兼容：云备份可能还在用旧文件名
        const val LOCAL_PHRASES_FILE = "pinyin_phrases.json"
    }

    /**
     * 下载 rime-ice 词库到 filesDir/rime/
     * 合并 base + 8105 为 pinyin.dict.yaml
     */
    fun downloadRimeDict(
        onProgress: (String) -> Unit,
        onComplete: (Boolean, String) -> Unit
    ) {
        Thread {
            try {
                val rimeDir = File(context.filesDir, "rime")
                rimeDir.mkdirs()

                // Step 1: 下载 cn_dicts.zip (~14MB)
                onProgress("正在下载词库 (~14MB)...")
                Log.i(TAG, "开始下载: $DICT_ZIP_URL")

                val client = OkHttpClient.Builder()
                    .connectTimeout(120, TimeUnit.SECONDS)
                    .readTimeout(300, TimeUnit.SECONDS)
                    .build()

                val request = Request.Builder().url(DICT_ZIP_URL).get().build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    onComplete(false, "下载失败: HTTP ${response.code}")
                    return@Thread
                }

                val body = response.body?.bytes() ?: byteArrayOf()
                if (body.isEmpty()) {
                    onComplete(false, "下载数据为空")
                    return@Thread
                }
                Log.i(TAG, "下载完成: ${body.size / 1024 / 1024}MB")

                // Step 2: 解压 zip
                onProgress("正在解压词库...")
                val tempZip = File(rimeDir, "cn_dicts_download.zip")
                tempZip.writeBytes(body)

                val zipFile = java.util.zip.ZipFile(tempZip)
                val entries = zipFile.entries()
                var extractedCount = 0
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    // 只提取我们需要的文件
                    val name = entry.name.substringAfterLast("/")
                    if (name == LOCAL_BASE_FILE || name == LOCAL_8105_FILE || name == "ext.dict.yaml") {
                        val outFile = File(rimeDir, name)
                        zipFile.getInputStream(entry).use { input ->
                            outFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        Log.i(TAG, "解压: $name (${outFile.length() / 1024}KB)")
                        extractedCount++
                    }
                }
                zipFile.close()
                tempZip.delete()

                if (extractedCount == 0) {
                    onComplete(false, "解压失败：未找到词库文件")
                    return@Thread
                }

                // Step 3: 合并为 pinyin.dict.yaml
                onProgress("正在合并词库...")
                mergeDicts(rimeDir)

                // 更新状态
                prefs.edit()
                    .putBoolean(PREF_DICT_DOWNLOADED, true)
                    .putString(PREF_DICT_VERSION, "rime-ice-${System.currentTimeMillis()}")
                    .putString(PREF_DICT_SOURCE, "rime-ice (GPL-3.0)")
                    .putLong(PREF_LAST_SYNC, System.currentTimeMillis())
                    .apply()

                val mergedFile = File(rimeDir, LOCAL_DICT_FILE)
                val entryCount = countDictEntries(mergedFile)
                onComplete(true, "词库下载完成！共 $entryCount 条词条")

            } catch (e: Exception) {
                Log.e(TAG, "下载 rime 词库失败", e)
                onComplete(false, "下载失败: ${e.message}")
            }
        }.start()
    }

    /**
     * 合并 base.dict.yaml + 8105.dict.yaml → pinyin.dict.yaml
     * 格式：汉字\t拼音\t词频（Rime 标准格式）
     */
    private fun mergeDicts(rimeDir: File) {
        val merged = LinkedHashMap<String, String>() // 汉字 → "拼音\t词频"

        // 先读 base（权重大，优先）
        val baseFile = File(rimeDir, LOCAL_BASE_FILE)
        if (baseFile.exists()) {
            baseFile.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && !trimmed.startsWith("---") && !trimmed.startsWith("...") && !trimmed.startsWith("name:") && !trimmed.startsWith("version:") && !trimmed.startsWith("sort:") && trimmed.contains("\t")) {
                        val parts = trimmed.split("\t")
                        if (parts.size >= 3) {
                            val hanzi = parts[0]
                            val pinyin = parts[1]
                            val weight = parts[2]
                            merged[hanzi] = "$pinyin\t$weight"
                        } else if (parts.size == 2) {
                            val hanzi = parts[0]
                            val pinyin = parts[1]
                            merged[hanzi] = "$pinyin\t100"
                        }
                    }
                }
            }
        }

        // 再读 8105（补充 base 没有的常用字）
        val dict8105File = File(rimeDir, LOCAL_8105_FILE)
        if (dict8105File.exists()) {
            dict8105File.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && !trimmed.startsWith("---") && !trimmed.startsWith("...") && !trimmed.startsWith("name:") && !trimmed.startsWith("version:") && !trimmed.startsWith("sort:") && trimmed.contains("\t")) {
                        val parts = trimmed.split("\t")
                        if (parts.size >= 2) {
                            val hanzi = parts[0]
                            if (!merged.containsKey(hanzi)) {
                                val pinyin = parts[1]
                                val weight = if (parts.size >= 3) parts[2] else "50"
                                merged[hanzi] = "$pinyin\t$weight"
                            }
                        }
                    }
                }
            }
        }

        // 写入合并后的 pinyin.dict.yaml
        val outFile = File(rimeDir, LOCAL_DICT_FILE)
        val sb = StringBuilder()
        sb.appendLine("# Rime dictionary — Cesia输入法")
        sb.appendLine("# 词库来源：rime-ice (iDvel/rime-ice)，GPL-3.0")
        sb.appendLine("# 合并：8105 字表 + base 基础词库")
        sb.appendLine("---")
        sb.appendLine("name: pinyin")
        sb.appendLine("version: \"1.1.1\"")
        sb.appendLine("sort: by_weight")
        sb.appendLine("...")
        sb.appendLine()
        for ((hanzi, value) in merged) {
            sb.appendLine("$hanzi\t$value")
        }
        outFile.writeText(sb.toString())
        Log.i(TAG, "合并词库: ${merged.size} 条 → ${outFile.absolutePath} (${outFile.length() / 1024 / 1024}MB)")
    }

    private fun countDictEntries(file: File): Int {
        if (!file.exists()) return 0
        var count = 0
        file.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && !trimmed.startsWith("---") && !trimmed.startsWith("...") && !trimmed.startsWith("name:") && !trimmed.startsWith("version:") && !trimmed.startsWith("sort:") && trimmed.contains("\t")) {
                    count++
                }
            }
        }
        return count
    }

    /**
     * 获取词库统计信息
     */
    fun getDictInfo(): DictInfo {
        val rimeDir = File(context.filesDir, "rime")
        val mergedFile = File(rimeDir, LOCAL_DICT_FILE)
        val baseFile = File(rimeDir, LOCAL_BASE_FILE)
        val dict8105File = File(rimeDir, LOCAL_8105_FILE)

        val dictCount = countDictEntries(mergedFile)
        val baseCount = countDictEntries(baseFile)
        val phraseCount = countDictEntries(dict8105File)
        val totalSize = mergedFile.length() + baseFile.length() + dict8105File.length()

        return DictInfo(
            dictCount = dictCount,
            phraseCount = phraseCount,
            dictSize = totalSize,
            phrasesSize = 0,
            version = prefs.getString(PREF_DICT_VERSION, "内置") ?: "内置",
            source = prefs.getString(PREF_DICT_SOURCE, "内置") ?: "内置",
            lastSync = prefs.getLong(PREF_LAST_SYNC, 0),
            downloaded = prefs.getBoolean(PREF_DICT_DOWNLOADED, false)
        )
    }

    /**
     * 检查是否已下载外部词库
     */
    fun hasDownloadedDict(): Boolean {
        return prefs.getBoolean(PREF_DICT_DOWNLOADED, false)
    }

    /**
     * 兼容旧版 PinyinEngine：返回外部词库路径（已废弃，始终返回 null）
     */
    fun getDictFilePath(): String? = null

    fun getPhrasesFilePath(): String? = null

    data class DictInfo(
        val dictCount: Int,
        val phraseCount: Int,
        val dictSize: Long,
        val phrasesSize: Long,
        val version: String,
        val source: String,
        val lastSync: Long,
        val downloaded: Boolean = false
    )
}
