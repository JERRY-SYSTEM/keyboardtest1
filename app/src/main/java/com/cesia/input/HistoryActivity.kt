package com.cesia.input

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cesia.input.stats.PolishStatsManager
import java.text.SimpleDateFormat
import java.util.*

class HistoryActivity : AppCompatActivity() {

    private lateinit var statsManager: PolishStatsManager
    private lateinit var adapter: HistoryAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        statsManager = PolishStatsManager(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // 顶部栏
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(32, 32, 32, 16)
        }

        val btnBack = Button(this).apply {
            text = "← 返回"
            setOnClickListener { finish() }
        }

        val btnClear = Button(this).apply {
            text = "🗑️ 清空"
            setOnClickListener {
                AlertDialog.Builder(this@HistoryActivity)
                    .setTitle("清空历史记录")
                    .setMessage("确定要清空所有润色历史记录吗？")
                    .setPositiveButton("清空") { _, _ ->
                        statsManager.clearRecords()
                        refreshData()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }

        topBar.addView(btnBack)
        topBar.addView(btnClear)
        root.addView(topBar)

        tvEmpty = TextView(this).apply {
            text = "暂无润色记录"
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            setPadding(32, 64, 32, 32)
            visibility = View.GONE
        }
        root.addView(tvEmpty)

        recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@HistoryActivity)
        }
        root.addView(recyclerView)

        setContentView(root)
        setTitle("润色历史记录")

        refreshData()
    }

    private fun refreshData() {
        val records = statsManager.getRecords()
        if (records.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            adapter = HistoryAdapter(records) { index ->
                AlertDialog.Builder(this)
                    .setTitle("删除记录")
                    .setMessage("删除这条记录？")
                    .setPositiveButton("删除") { _, _ ->
                        statsManager.deleteRecord(index)
                        refreshData()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
            recyclerView.adapter = adapter
        }
    }

    class HistoryAdapter(
        private val records: List<com.cesia.input.stats.PolishRecord>,
        private val onDelete: (Int) -> Unit
    ) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTime: TextView = view.findViewById(R.id.tv_record_time)
            val tvInput: TextView = view.findViewById(R.id.tv_record_input)
            val tvOutput: TextView = view.findViewById(R.id.tv_record_output)
            val btnDelete: ImageButton = view.findViewById(R.id.btn_delete_record)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_history_record, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val record = records[position]
            val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            holder.tvTime.text = sdf.format(Date(record.timestamp))
            holder.tvInput.text = record.inputText
            holder.tvOutput.text = record.outputText
            holder.btnDelete.setOnClickListener { onDelete(position) }
        }

        override fun getItemCount() = records.size
    }
}
