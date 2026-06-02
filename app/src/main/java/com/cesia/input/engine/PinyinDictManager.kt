package com.cesia.input.engine

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Rime 词库管理器
 * 支持分词库下载：基础词库（8105字表+base+英文+opencc）、扩展词库（41448+ext）、腾讯词库
 *
 * 词库来源：rime-ice (iDvel/rime-ice)，GPL-3.0 许可证
 */
class PinyinDictManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("cesia_dict", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "RimeDictManager"
        const val PREF_DICT_DOWNLOADED = "dict_downloaded"
        const val PREF_BASE_DOWNLOADED = "base_downloaded"
        const val PREF_TENCENT_DOWNLOADED = "tencent_downloaded"
        const val PREF_EN_DOWNLOADED = "en_downloaded"
        const val PREF_OPENCC_DOWNLOADED = "opencc_downloaded"
        const val PREF_LAST_SYNC = "last_sync"

        // === 下载源 ===
        const val CN_DICTS_URL = "https://github.com/iDvel/rime-ice/releases/download/nightly/cn_dicts.zip"
        const val EN_DICTS_URL = "https://github.com/iDvel/rime-ice/releases/download/nightly/en_dicts.zip"
        const val OPENCC_URL = "https://github.com/iDvel/rime-ice/releases/download/nightly/opencc.zip"

        // === 本地目录 ===
        private const val RIME_DIR = "rime"
        const val LOCAL_DICT_FILE = "pinyin.dict.yaml"
        const val LOCAL_8105_FILE = "8105.dict.yaml"

        // === 词包定义 ===
        const val BUNDLE_BASE = "base"
        const val BUNDLE_TENCENT = "tencent"
    }

    data class BundleInfo(
        val id: String,
        val name: String,
        val description: String,
        val estimatedSize: String,
        val required: Boolean,
        val recommended: Boolean,
        val url: String,
        val files: List<String>
    )

    fun getAvailableBundles(): List<BundleInfo> = listOf(
        BundleInfo(
            id = BUNDLE_BASE,
            name = "基础词库",
            description = "8105字表（~8000字）+ 基础词库（~55万词条）+ 英文词库 + OpenCC",
            estimatedSize = "~20MB",
            required = true,
            recommended = true,
            url = CN_DICTS_URL,
            files = listOf("base.dict.yaml")
        ),
        BundleInfo(
            id = BUNDLE_TENCENT,
            name = "腾讯词库",
            description = "腾讯词库（~100万词条）",
            estimatedSize = "~17MB",
            required = false,
            recommended = false,
            url = CN_DICTS_URL,
            files = listOf("tencent.dict.yaml")
        )
    )

    fun downloadBundles(
        bundles: List<String>,
        onProgress: (String) -> Unit,
        onComplete: (Boolean, String) -> Unit
    ) {
        if (bundles.isEmpty()) {
            onComplete(false, "请选择要下载的词库包")
            return
        }

        Thread {
            try {
                val rimeDir = File(context.filesDir, RIME_DIR)
                rimeDir.mkdirs()

                var totalExtracted = 0

                // 下载中文词库
                val cnFiles = mutableListOf<String>()
                if (bundles.contains(BUNDLE_BASE)) {
                    cnFiles.add("base.dict.yaml")
                    cnFiles.add("8105.dict.yaml")
                }
                if (bundles.contains(BUNDLE_TENCENT)) {
                    cnFiles.add("tencent.dict.yaml")
                }

                onProgress("正在下载中文词库...")
                totalExtracted += downloadAndExtract(CN_DICTS_URL, rimeDir, cnFiles)

                // 下载英文词库（随基础包）
                if (bundles.contains(BUNDLE_BASE)) {
                    onProgress("正在下载英文词库...")
                    val enFiles = listOf("en.dict.yaml", "en_ext.dict.yaml", "en_aliases.dict.yaml")
                    totalExtracted += downloadAndExtract(EN_DICTS_URL, rimeDir, enFiles)
                }

                // 下载 OpenCC（随基础包）
                if (bundles.contains(BUNDLE_BASE)) {
                    onProgress("正在下载 OpenCC 转换表...")
                    val openccDir = File(rimeDir, "opencc")
                    openccDir.mkdirs()
                    val openccFiles = listOf("STPhrases.txt", "STCharacters.txt", "TSCharacters.txt",
                        "HKVariants.txt", "JPVariants.txt", "TWVariants.txt")
                    totalExtracted += downloadAndExtract(OPENCC_URL, openccDir, openccFiles)
                }

                if (totalExtracted == 0) {
                    onComplete(false, "未下载到任何词库文件")
                    return@Thread
                }

                // 合并选中的词库
                onProgress("正在合并词库...")
                val entryCount = mergeSelectedDicts(rimeDir, bundles)

                // 更新状态
                updateBundlePrefs(bundles)
                onComplete(true, "词库下载完成！共 ${entryCount} 条词条")

            } catch (e: Exception) {
                Log.e(TAG, "下载词库失败", e)
                onComplete(false, "下载失败: ${e.message}")
            }
        }.start()
    }

    private fun downloadAndExtract(url: String, destDir: File, targetFiles: List<String>): Int {
        Log.i(TAG, "下载: $url, 目标文件: $targetFiles")

        val request = Request.Builder().url(url).get().build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            Log.w(TAG, "下载失败: HTTP ${response.code} for $url")
            return 0
        }

        val body = response.body?.bytes() ?: return 0
        Log.i(TAG, "下载完成: ${body.size / 1024}KB")

        var extracted = 0
        val zis = ZipInputStream(body.inputStream())
        var entry: ZipEntry? = zis.nextEntry
        while (entry != null) {
            val name = entry.name.substringAfterLast("/")
            if (!entry.isDirectory && targetFiles.any { name == it }) {
                val outFile = File(destDir, name)
                outFile.outputStream().use { output ->
                    zis.copyTo(output)
                }
                Log.i(TAG, "解压: $name (${outFile.length() / 1024}KB)")
                extracted++
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }
        zis.close()

        return extracted
    }

    private fun mergeSelectedDicts(rimeDir: File, bundles: List<String>): Int {
        val entries = LinkedHashMap<String, Pair<String, Long>>()

        // Step 1: 基础词库（base）- 词组优先
        if (bundles.contains(BUNDLE_BASE)) {
            val baseFile = File(rimeDir, "base.dict.yaml")
            if (baseFile.exists()) {
                val before = entries.size
                loadDictEntries(baseFile, entries, isCharTable = false)
                Log.i(TAG, "加载 base: +${entries.size - before} 条, 当前总条目: ${entries.size}")
            }
        }

        // Step 2: 腾讯词库（最大，最后加载，优先级最高）
        if (bundles.contains(BUNDLE_TENCENT)) {
            val tencentFile = File(rimeDir, "tencent.dict.yaml")
            if (tencentFile.exists()) {
                val before = entries.size
                loadDictEntries(tencentFile, entries, isCharTable = false)
                Log.i(TAG, "加载 tencent: +${entries.size - before} 条")
            }
        }

        // Step 3: 8105 字表（最后加载，确保单字覆盖词组中的同音字）
        val charTableFile = File(rimeDir, LOCAL_8105_FILE)
        if (charTableFile.exists()) {
            val before = entries.size
            // 字表直接覆盖，不检查 isCharTable
            loadDictEntries(charTableFile, entries, isCharTable = false)
            Log.i(TAG, "加载字表: ${charTableFile.name}, +${entries.size - before} 条, 当前总条目: ${entries.size}")
        }

        // 写入合并后的 pinyin.dict.yaml
        val outFile = File(rimeDir, LOCAL_DICT_FILE)
        val sb = StringBuilder()
        sb.appendLine("# Rime dictionary — Cesia输入法")
        sb.appendLine("# 词库来源：rime-ice (iDvel/rime-ice)，GPL-3.0")
        sb.appendLine("# 合并：${bundles.joinToString(" + ")}")
        sb.appendLine("---")
        sb.appendLine("name: pinyin")
        sb.appendLine("version: \"1.1.1\"")
        sb.appendLine("sort: by_weight")
        sb.appendLine("...")
        sb.appendLine()
        for ((hanzi, pair) in entries) {
            sb.appendLine("$hanzi\t${pair.first}\t${pair.second}")
        }
        outFile.writeText(sb.toString())

        Log.i(TAG, "合并完成: ${entries.size} 条 → ${outFile.name} (${outFile.length() / 1024 / 1024}MB)")
        return entries.size
    }

    private fun loadDictEntries(file: File, entries: LinkedHashMap<String, Pair<String, Long>>, isCharTable: Boolean) {
        file.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("---") ||
                    trimmed.startsWith("...") || trimmed.startsWith("name:") ||
                    trimmed.startsWith("version:") || trimmed.startsWith("sort:")) {
                    return@forEach
                }
                val parts = trimmed.split("\t")
                if (parts.size >= 3) {
                    val hanzi = parts[0]
                    val pinyin = parts[1]
                    val weight = parts[2].toLongOrNull() ?: 1L
                    if (isCharTable) {
                        if (!entries.containsKey(hanzi)) {
                            entries[hanzi] = Pair(pinyin, weight)
                        }
                    } else {
                        entries[hanzi] = Pair(pinyin, weight)
                    }
                } else if (parts.size == 2) {
                    val hanzi = parts[0]
                    val pinyin = parts[1]
                    if (isCharTable) {
                        if (!entries.containsKey(hanzi)) {
                            entries[hanzi] = Pair(pinyin, 50L)
                        }
                    } else {
                        entries[hanzi] = Pair(pinyin, 100L)
                    }
                }
            }
        }
    }

    private fun updateBundlePrefs(bundles: List<String>) {
        val editor = prefs.edit()
        if (bundles.contains(BUNDLE_BASE)) {
            editor.putBoolean(PREF_BASE_DOWNLOADED, true)
            editor.putBoolean(PREF_EN_DOWNLOADED, true)
            editor.putBoolean(PREF_OPENCC_DOWNLOADED, true)
        }
        if (bundles.contains(BUNDLE_TENCENT)) {
            editor.putBoolean(PREF_TENCENT_DOWNLOADED, true)
        }
        editor.putBoolean(PREF_DICT_DOWNLOADED, true)
        editor.putLong(PREF_LAST_SYNC, System.currentTimeMillis())
        editor.apply()
    }

    private fun countDictEntries(file: File): Int {
        if (!file.exists()) return 0
        var count = 0
        file.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && !trimmed.startsWith("---") &&
                    !trimmed.startsWith("...") && !trimmed.startsWith("name:") &&
                    !trimmed.startsWith("version:") && !trimmed.startsWith("sort:") &&
                    trimmed.contains("\t")) {
                    count++
                }
            }
        }
        return count
    }

    fun getDictInfo(): DictInfo {
        val rimeDir = File(context.filesDir, RIME_DIR)
        val mergedFile = File(rimeDir, LOCAL_DICT_FILE)
        val dictCount = if (mergedFile.exists()) countDictEntries(mergedFile) else 0
        val totalSize = rimeDir.listFiles()?.sumOf { it.length() } ?: 0

        val bundleNames = mutableListOf<String>()
        if (prefs.getBoolean(PREF_BASE_DOWNLOADED, false)) bundleNames.add("基础")
        if (prefs.getBoolean(PREF_TENCENT_DOWNLOADED, false)) bundleNames.add("腾讯")

        return DictInfo(
            dictCount = dictCount,
            dictSize = totalSize,
            downloaded = prefs.getBoolean(PREF_DICT_DOWNLOADED, false),
            bundles = bundleNames,
            lastSync = prefs.getLong(PREF_LAST_SYNC, 0)
        )
    }

    fun hasDownloadedDict(): Boolean = prefs.getBoolean(PREF_DICT_DOWNLOADED, false)

    fun getMergedDictPath(): String? {
        val f = File(context.filesDir, "$RIME_DIR/$LOCAL_DICT_FILE")
        return if (f.exists()) f.absolutePath else null
    }

    fun getDictFilePath(): String? = getMergedDictPath()

    fun getPhrasesFilePath(): String? = null

    data class DictInfo(
        val dictCount: Int,
        val dictSize: Long,
        val downloaded: Boolean,
        val bundles: List<String> = emptyList(),
        val lastSync: Long
    )
}
