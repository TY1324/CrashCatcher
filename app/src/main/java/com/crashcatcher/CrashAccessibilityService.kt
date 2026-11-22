package com.crashcatcher

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 无障碍服务：监听系统弹出的「XX 已停止运行 / 无响应」窗口，
 * 自动记录是哪个 App、什么时候、什么类型。无需电脑授权。
 */
class CrashAccessibilityService : AccessibilityService() {

    companion object {
        private var lastText = ""
        private var lastTime = 0L
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        val type = e.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            type != AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
        ) return

        val pkg = e.packageName?.toString() ?: ""
        if (pkg == packageName) return

        val texts = mutableListOf<String>()
        if (e.text != null) {
            for (t in e.text) {
                if (!t.isNullOrBlank()) texts.add(t.toString())
            }
        }
        if (!e.contentDescription.isNullOrBlank()) texts.add(e.contentDescription.toString())
        val root = rootInActiveWindow
        if (root != null) collect(root, texts, 0)
        if (texts.isEmpty()) return

        val all = texts.joinToString("\n")
        val isCrash = all.contains("已停止运行") || all.contains("停止运行") ||
            all.contains("has stopped") || all.contains("keeps stopping")
        val isAnr = all.contains("无响应") || all.contains("未响应") ||
            all.contains("isn't responding") || all.contains("is not responding")
        if (!isCrash && !isAnr) return

        val now = System.currentTimeMillis()
        val key = all.take(200)
        if (key == lastText && now - lastTime < 15000) return
        lastText = key
        lastTime = now

        val record = CrashRecord(
            id = "$now-${(0..9999).random()}",
            time = now,
            packageName = guessAppName(all),
            type = if (isAnr) "无响应（无障碍）" else "崩溃（无障碍）",
            message = all.replace("\n", " ").take(300),
            log = "来源：无障碍服务检测（系统弹窗）\n\n$all"
        )
        CrashStore.add(this, record)
    }

    private fun collect(node: AccessibilityNodeInfo?, out: MutableList<String>, depth: Int) {
        if (node == null || depth > 25) return
        val t = node.text?.toString()
        if (!t.isNullOrBlank() && !out.contains(t)) out.add(t)
        val d = node.contentDescription?.toString()
        if (!d.isNullOrBlank() && !out.contains(d)) out.add(d)
        for (i in 0 until node.childCount) {
            collect(node.getChild(i), out, depth + 1)
        }
    }

    private fun guessAppName(all: String): String {
        val quoted = Regex("[“\"「『]([^”\"」』]{1,40})[”\"」』]").find(all)
        if (quoted != null) return quoted.groupValues[1].trim()
        val m = Regex("([^\\s，,。]{1,40})\\s*(?:已停止运行|屡次停止运行|无响应|未响应)").find(all)
        if (m != null) return m.groupValues[1].trim()
        return all.substringBefore("已停止运行").trim().trim('，', ',', ' ').ifBlank { "未知应用" }
    }

    override fun onInterrupt() {
    }
}
