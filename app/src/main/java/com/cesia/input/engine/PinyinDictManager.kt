package com.cesia.input.engine

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 拼音词库管理器
 * 支持从网络下载词库、导入导出、云端备份
 */
class PinyinDictManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("cesia_dict", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "PinyinDictManager"
        const val PREF_DICT_VERSION = "dict_version"
        const val PREF_DICT_SOURCE = "dict_source"
        const val PREF_LAST_SYNC = "last_sync"

        // 词库下载源 - 从GitHub仓库assets目录下载（CC-CEDICT 10万+词组）
        const val DEFAULT_DICT_URL = "https://raw.githubusercontent.com/harviex/cesia-input-method/main/app/src/main/assets/pinyin_dict.json"
        const val DEFAULT_PHRASES_URL = "https://raw.githubusercontent.com/harviex/cesia-input-method/main/app/src/main/assets/pinyin_phrases.json"

        // 本地词库文件
        const val LOCAL_DICT_FILE = "pinyin_dict.json"
        const val LOCAL_PHRASES_FILE = "pinyin_phrases.json"
        const val EXPORT_DICT_FILE = "pinyin_dict_export.json"
        const val EXPORT_PHRASES_FILE = "pinyin_phrases_export.json"
    }

    /**
     * 从网络下载词库
     */
    fun downloadDict(
        dictUrl: String = DEFAULT_DICT_URL,
        phrasesUrl: String = DEFAULT_PHRASES_URL,
        onProgress: (String) -> Unit,
        onComplete: (Boolean, String) -> Unit
    ) {
        Thread {
            try {
                onProgress("正在下载拼音字典...")

                // 下载主字典
                val dictRequest = Request.Builder().url(dictUrl).get().build()
                val dictResponse = client.newCall(dictRequest).execute()
                if (!dictResponse.isSuccessful) {
                    onComplete(false, "字典下载失败: HTTP ${dictResponse.code}")
                    return@Thread
                }
                val dictJson = dictResponse.body?.string() ?: ""
                if (dictJson.isEmpty()) {
                    onComplete(false, "字典数据为空")
                    return@Thread
                }

                // 验证JSON格式
                try {
                    JSONObject(dictJson)
                } catch (e: Exception) {
                    onComplete(false, "字典JSON格式错误: ${e.message}")
                    return@Thread
                }

                onProgress("正在下载词组字典...")

                // 下载词组字典
                val phrasesRequest = Request.Builder().url(phrasesUrl).get().build()
                val phrasesResponse = client.newCall(phrasesRequest).execute()
                if (!phrasesResponse.isSuccessful) {
                    onComplete(false, "词组下载失败: HTTP ${phrasesResponse.code}")
                    return@Thread
                }
                val phrasesJson = phrasesResponse.body?.string() ?: ""
                if (phrasesJson.isEmpty()) {
                    onComplete(false, "词组数据为空")
                    return@Thread
                }

                try {
                    JSONObject(phrasesJson)
                } catch (e: Exception) {
                    onComplete(false, "词组JSON格式错误: ${e.message}")
                    return@Thread
                }

                onProgress("正在保存词库...")

                // 保存到assets目录（实际上是保存到files目录，因为assets是只读的）
                val dictFile = File(context.filesDir, LOCAL_DICT_FILE)
                val phrasesFile = File(context.filesDir, LOCAL_PHRASES_FILE)

                dictFile.writeText(dictJson)
                phrasesFile.writeText(phrasesJson)

                // 更新版本信息
                val version = System.currentTimeMillis().toString()
                prefs.edit()
                    .putString(PREF_DICT_VERSION, version)
                    .putString(PREF_DICT_SOURCE, "network")
                    .putLong(PREF_LAST_SYNC, System.currentTimeMillis())
                    .apply()

                val dictCount = JSONObject(dictJson).keys().asSequence().count()
                val phraseCount = JSONObject(phrasesJson).keys().asSequence().count()

                onComplete(true, "词库下载完成！字典: $dictCount 条，词组: $phraseCount 条")

            } catch (e: Exception) {
                Log.e(TAG, "下载词库失败", e)
                onComplete(false, "下载失败: ${e.message}")
            }
        }.start()
    }

    /**
     * 导入词库文件
     */
    fun importDict(dictPath: String?, phrasesPath: String?, onComplete: (Boolean, String) -> Unit) {
        if (dictPath == null && phrasesPath == null) {
            onComplete(false, "请选择要导入的文件")
            return
        }

        try {
            var dictCount = 0
            var phraseCount = 0

            if (dictPath != null) {
                val dictFile = File(dictPath)
                if (!dictFile.exists()) {
                    onComplete(false, "字典文件不存在: $dictPath")
                    return
                }
                val dictJson = dictFile.readText()
                JSONObject(dictJson) // 验证格式

                val targetFile = File(context.filesDir, LOCAL_DICT_FILE)
                targetFile.writeText(dictJson)
                dictCount = JSONObject(dictJson).keys().asSequence().count()
            }

            if (phrasesPath != null) {
                val phrasesFile = File(phrasesPath)
                if (!phrasesFile.exists()) {
                    onComplete(false, "词组文件不存在: $phrasesPath")
                    return
                }
                val phrasesJson = phrasesFile.readText()
                JSONObject(phrasesJson) // 验证格式

                val targetFile = File(context.filesDir, LOCAL_PHRASES_FILE)
                targetFile.writeText(phrasesJson)
                phraseCount = JSONObject(phrasesJson).keys().asSequence().count()
            }

            prefs.edit()
                .putString(PREF_DICT_VERSION, System.currentTimeMillis().toString())
                .putString(PREF_DICT_SOURCE, "import")
                .putLong(PREF_LAST_SYNC, System.currentTimeMillis())
                .apply()

            onComplete(true, "导入成功！字典: $dictCount 条，词组: $phraseCount 条")

        } catch (e: Exception) {
            Log.e(TAG, "导入词库失败", e)
            onComplete(false, "导入失败: ${e.message}")
        }
    }

    /**
     * 导出词库到指定目录
     */
    fun exportDict(exportDir: String, onComplete: (Boolean, String) -> Unit) {
        try {
            val dictFile = File(context.filesDir, LOCAL_DICT_FILE)
            val phrasesFile = File(context.filesDir, LOCAL_PHRASES_FILE)

            if (!dictFile.exists() && !phrasesFile.exists()) {
                onComplete(false, "没有可导出的词库")
                return
            }

            val dir = File(exportDir)
            if (!dir.exists()) dir.mkdirs()

            var exportedCount = 0

            if (dictFile.exists()) {
                val exportFile = File(dir, EXPORT_DICT_FILE)
                exportFile.writeText(dictFile.readText())
                exportedCount++
            }

            if (phrasesFile.exists()) {
                val exportFile = File(dir, EXPORT_PHRASES_FILE)
                exportFile.writeText(phrasesFile.readText())
                exportedCount++
            }

            onComplete(true, "已导出 $exportedCount 个词库文件到: $exportDir")

        } catch (e: Exception) {
            Log.e(TAG, "导出词库失败", e)
            onComplete(false, "导出失败: ${e.message}")
        }
    }

    /**
     * 获取词库统计信息
     */
    fun getDictInfo(): DictInfo {
        val dictFile = File(context.filesDir, LOCAL_DICT_FILE)
        val phrasesFile = File(context.filesDir, LOCAL_PHRASES_FILE)

        var dictCount = 0
        var phraseCount = 0
        var dictSize = 0L
        var phrasesSize = 0L

        if (dictFile.exists()) {
            dictSize = dictFile.length()
            try {
                dictCount = JSONObject(dictFile.readText()).keys().asSequence().count()
            } catch (_: Exception) {}
        }

        if (phrasesFile.exists()) {
            phrasesSize = phrasesFile.length()
            try {
                phraseCount = JSONObject(phrasesFile.readText()).keys().asSequence().count()
            } catch (_: Exception) {}
        }

        return DictInfo(
            dictCount = dictCount,
            phraseCount = phraseCount,
            dictSize = dictSize,
            phrasesSize = phrasesSize,
            version = prefs.getString(PREF_DICT_VERSION, "未知") ?: "未知",
            source = prefs.getString(PREF_DICT_SOURCE, "内置") ?: "内置",
            lastSync = prefs.getLong(PREF_LAST_SYNC, 0)
        )
    }

    /**
     * 检查是否有外部词库
     */
    fun hasExternalDict(): Boolean {
        val dictFile = File(context.filesDir, LOCAL_DICT_FILE)
        val phrasesFile = File(context.filesDir, LOCAL_PHRASES_FILE)
        return dictFile.exists() || phrasesFile.exists()
    }

    /**
     * 获取词库文件路径（供PinyinEngine使用）
     */
    fun getDictFilePath(): String? {
        val file = File(context.filesDir, LOCAL_DICT_FILE)
        return if (file.exists()) file.absolutePath else null
    }

    fun getPhrasesFilePath(): String? {
        val file = File(context.filesDir, LOCAL_PHRASES_FILE)
        return if (file.exists()) file.absolutePath else null
    }

    data class DictInfo(
        val dictCount: Int,
        val phraseCount: Int,
        val dictSize: Long,
        val phrasesSize: Long,
        val version: String,
        val source: String,
        val lastSync: Long
    )
}
