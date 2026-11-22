package com.crashcatcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机后自动开始监控（部分厂商需允许自启动）。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            try {
                if (MonitorService.isRunning) return
                MonitorService.start(context)
            } catch (_: Exception) {
            }
        }
    }
}
