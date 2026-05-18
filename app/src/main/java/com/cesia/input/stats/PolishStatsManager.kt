package com.cesia.input.stats

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class PolishRecord(
    val timestamp: Long,
    val inputText: String,
    val outputText: String,
    val inputChars: Int,
    val outputChars: Int
)

class PolishStatsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("cesia_polish_stats", Context.MODE_PRIVATE)
    private val recordsPrefs: SharedPreferences = context.getSharedPreferences("cesia_polish_records", Context.MODE_PRIVATE)

    // 统计数据
    var totalInputChars: Int
        get() = prefs.getInt("total_input_chars", 0)
        set(value) = prefs.edit().putInt("total_input_chars", value).apply()

    var totalOutputChars: Int
        get() = prefs.getInt("total_output_chars", 0)
        set(value) = prefs.edit().putInt("total_output_chars", value).apply()

    var totalPolishCount: Int
        get() = prefs.getInt("total_polish_count", 0)
        set(value) = prefs.edit().putInt("total_polish_count", value).apply()

    fun addRecord(inputText: String, outputText: String) {
        totalInputChars += inputText.length
        totalOutputChars += outputText.length
        totalPolishCount++

        // 保存记录（最多100条）
        val records = getRecords().toMutableList()
        records.add(0, PolishRecord(
            timestamp = System.currentTimeMillis(),
            inputText = inputText,
            outputText = outputText,
            inputChars = inputText.length,
            outputChars = outputText.length
        ))
        // 只保留最近100条
        val trimmed = records.take(100)
        saveRecords(trimmed)
    }

    fun getRecords(): List<PolishRecord> {
        val json = recordsPrefs.getString("records", "[]") ?: "[]"
        val arr = JSONArray(json)
        val list = mutableListOf<PolishRecord>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(PolishRecord(
                timestamp = obj.getLong("timestamp"),
                inputText = obj.getString("input"),
                outputText = obj.getString("output"),
                inputChars = obj.getInt("inputChars"),
                outputChars = obj.getInt("outputChars")
            ))
        }
        return list
    }

    fun clearRecords() {
        recordsPrefs.edit().putString("records", "[]").apply()
        prefs.edit().putInt("total_input_chars", 0)
            .putInt("total_output_chars", 0)
            .putInt("total_polish_count", 0).apply()
    }

    fun deleteRecord(index: Int) {
        val records = getRecords().toMutableList()
        if (index in records.indices) {
            records.removeAt(index)
            saveRecords(records)
        }
    }

    private fun saveRecords(records: List<PolishRecord>) {
        val arr = JSONArray()
        for (r in records) {
            arr.put(JSONObject().apply {
                put("timestamp", r.timestamp)
                put("input", r.inputText)
                put("output", r.outputText)
                put("inputChars", r.inputChars)
                put("outputChars", r.outputChars)
            })
        }
        recordsPrefs.edit().putString("records", arr.toString()).apply()
    }
}
