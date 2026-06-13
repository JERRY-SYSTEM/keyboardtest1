package com.cesia.input.stats

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * 魔法修改历史记录管理器
 *
 * 保存用户每次使用的魔法修改指令（如"将文字改为英文"），
 * 支持置顶、最近优先排序。
 */
class MagicHistoryManager(context: Context) {

    data class MagicRecord(
        val id: Long,
        val instruction: String,
        val isPinned: Boolean = false,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val prefs: SharedPreferences = context.getSharedPreferences("cesia_magic_history", Context.MODE_PRIVATE)
    private val listPrefs: SharedPreferences = context.getSharedPreferences("cesia_magic_records", Context.MODE_PRIVATE)

    /** 默认魔法指令列表（从指令表加载） */
    private val defaultInstructions: List<String> by lazy {
        // 从 InstructionSet 加载全部标准指令名称
        com.cesia.input.instruction.InstructionSet.allInstructions.map { it.name }
    }

    init {
        // 首次使用时注入全部标准指令
        val initialized = listPrefs.getBoolean("initialized", false)
        if (!initialized) {
            val records = getRecords()
            if (records.isEmpty()) {
                val now = System.currentTimeMillis()
                val defaultRecords = defaultInstructions.mapIndexed { index, instruction ->
                    MagicRecord(
                        id = index.toLong() + 1,
                        instruction = instruction,
                        isPinned = false,
                        timestamp = now - (defaultInstructions.size - index).toLong() * 1000
                    )
                }
                saveRecords(defaultRecords)
            }
            listPrefs.edit().putBoolean("initialized", true).apply()
        }
        // 每次启动：同步 InstructionSet 中新增的指令到历史记录
        syncInstructionSet()
    }

    /** 将 InstructionSet 中有但历史记录中没有的指令追加进去 */
    private fun syncInstructionSet() {
        val existing = getRecords()
        val existingNames = existing.map { it.instruction }.toSet()
        val allStdNames = defaultInstructions.toSet()
        // 找出有但历史记录中没有的指令
        val missing = allStdNames - existingNames
        if (missing.isNotEmpty()) {
            val maxId = existing.maxOfOrNull { it.id } ?: 0L
            val now = System.currentTimeMillis()
            val newRecords = missing.mapIndexed { index, name ->
                MagicRecord(
                    id = maxId + index + 1,
                    instruction = name,
                    isPinned = false,
                    timestamp = now - index.toLong() * 1000
                )
            }
            saveRecords(existing + newRecords)
            Log.i("MagicHistory", "同步了 ${newRecords.size} 条新指令到魔法书")
        }
    }

    /** 获取所有记录（置顶优先，再按时间倒序） */
    fun getRecords(): List<MagicRecord> {
        val json = listPrefs.getString("records", "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<MagicRecord>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(MagicRecord(
                    id = obj.optLong("id", i.toLong()),
                    instruction = obj.getString("instruction"),
                    isPinned = obj.optBoolean("isPinned", false),
                    timestamp = obj.optLong("timestamp", 0L)
                ))
            }
            // 置顶优先，再按时间倒序
            list.sortedWith(compareByDescending<MagicRecord> { it.isPinned }.thenByDescending { it.timestamp })
        } catch (e: Exception) {
            Log.e("MagicHistory", "解析失败", e)
            emptyList()
        }
    }

    /** 添加新记录（重复指令只更新时间+移动位置） */
    fun addRecord(instruction: String) {
        if (instruction.isBlank()) return

        val records = getRecords()
        val existing = records.find { it.instruction == instruction }

        val newList = mutableListOf<MagicRecord>()
        if (existing != null) {
            // 更新已存在的记录的时间戳（移到最前）
            for (r in records) {
                if (r.id == existing.id) {
                    newList.add(MagicRecord(r.id, r.instruction, r.isPinned, System.currentTimeMillis()))
                } else {
                    newList.add(r)
                }
            }
        } else {
            val newId = (records.maxOfOrNull { it.id } ?: 0L) + 1
            newList.add(MagicRecord(newId, instruction, timestamp = System.currentTimeMillis()))
            newList.addAll(records)
        }

        // 最多保留30条
        val trimmed = newList.take(30)
        saveRecords(trimmed)
    }

    /** 切换置顶状态 */
    fun togglePin(id: Long) {
        val records = getRecords()
        val updated = records.map { r ->
            if (r.id == id) r.copy(isPinned = !r.isPinned)
            else r
        }
        saveRecords(updated)
    }

    /** 删除记录 */
    fun removeRecord(id: Long) {
        val records = getRecords()
        saveRecords(records.filter { it.id != id })
    }

    /** 删除多条记录 */
    fun removeRecords(ids: List<Long>) {
        val records = getRecords()
        saveRecords(records.filter { it.id !in ids })
    }

    /** 清空所有记录 */
    fun clearAll() {
        saveRecords(emptyList())
    }

    /** 获取已置顶的指令（如果有） */
    fun getActiveInstruction(): String? {
        val records = getRecords()
        // 返回第一个置顶的记录，如果没有则返回最近使用的
        val pinned = records.firstOrNull { it.isPinned }
        return pinned?.instruction ?: records.firstOrNull()?.instruction
    }

    private fun saveRecords(records: List<MagicRecord>) {
        val arr = JSONArray()
        for (r in records) {
            arr.put(JSONObject().apply {
                put("id", r.id)
                put("instruction", r.instruction)
                put("isPinned", r.isPinned)
                put("timestamp", r.timestamp)
            })
        }
        listPrefs.edit().putString("records", arr.toString()).apply()
    }
}
