package com.cesia.input

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 无障碍屏幕内容读取服务
 *
 * 功能：监听窗口变化事件，遍历当前前台 App 的 UI 树，提取所有文本内容
 * 用途：为星星按钮的 AI 回复提供屏幕上下文
 *
 * 注意：
 * - 仅读取文本，不做任何修改
 * - 用户需在系统设置中手动开启此无障碍服务
 * - 部分 App（如微信）可能对无障碍读取做了限制
 */
class ScreenReaderService : AccessibilityService() {

    companion object {
        private const val TAG = "CesiaScreenReader"
        private const val MAX_TEXT_LENGTH = 3000  // 限制最大读取字符数，避免 prompt 过长

        /** 最近一次读取的屏幕文本（缓存） */
        @Volatile
        var cachedScreenText: String = ""
            private set

        /** 最近一次读取时间戳 */
        @Volatile
        var lastReadTime: Long = 0L
            private set

        /** 读取超时：超过此时间认为缓存失效（毫秒） */
        private const val CACHE_TIMEOUT_MS = 5000L

        /**
         * 获取当前屏幕文本
         * 如果缓存未过期则返回缓存，否则返回空字符串
         */
        fun getScreenText(): String {
            val now = System.currentTimeMillis()
            return if (now - lastReadTime < CACHE_TIMEOUT_MS && cachedScreenText.isNotEmpty()) {
                cachedScreenText
            } else {
                ""
            }
        }

        /**
         * 清除缓存（切换 App 或输入框变化时调用）
         */
        fun clearCache() {
            cachedScreenText = ""
            lastReadTime = 0L
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "无障碍屏幕读取服务已连接")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // 只处理窗口状态变化和内容变化事件
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val rootNode = rootInActiveWindow ?: return
                try {
                    val text = extractTextFromNode(rootNode)
                    if (text.isNotEmpty()) {
                        cachedScreenText = text.take(MAX_TEXT_LENGTH)
                        lastReadTime = System.currentTimeMillis()
                        Log.d(TAG, "屏幕内容已更新: ${cachedScreenText.length} 字符")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "读取屏幕内容失败", e)
                } finally {
                    rootNode.recycle()
                }
            }
        }
    }

    /**
     * 递归遍历 UI 节点树，提取所有文本内容
     */
    private fun extractTextFromNode(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""

        val sb = StringBuilder()

        // 提取当前节点的文本
        node.text?.let { text ->
            if (text.isNotBlank()) {
                sb.append(text.trim()).append("\n")
            }
        }

        // 提取 contentDescription（部分 App 用这个存文本）
        node.contentDescription?.let { desc ->
            if (desc.isNotBlank() && desc.toString() != node.text?.toString()) {
                sb.append(desc.trim()).append("\n")
            }
        }

        // 递归遍历子节点
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                val childText = extractTextFromNode(child)
                if (childText.isNotBlank()) {
                    sb.append(childText)
                }
                child.recycle()
            }
        }

        return sb.toString().trim()
    }

    override fun onInterrupt() {
        Log.w(TAG, "无障碍屏幕读取服务被中断")
    }

    override fun onDestroy() {
        super.onDestroy()
        clearCache()
        Log.i(TAG, "无障碍屏幕读取服务已销毁")
    }
}
