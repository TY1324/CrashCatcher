package com.crashcatcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MonitorService : Service() {

    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val CHANNEL_ID = "monitor_channel"
        const val NOTIF_ID = 1001
        const val ACTION_START = "action_start"
        const val ACTION_STOP = "action_stop"

        @Volatile
        var isRunning = false

        fun start(context: Context) {
            val i = Intent(context, MonitorService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: Context) {
            val i = Intent(context, MonitorService::class.java).setAction(ACTION_STOP)
            context.startService(i)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopMonitoring()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForegroundWithNotification()
                if (!isRunning) startMonitoring()
            }
        }
        return START_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "崩溃监控", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(text: String): Notification {
        createChannel()
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("崩溃捕手")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    private fun startForegroundWithNotification() {
        val n = buildNotification("正在监控应用崩溃…")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun startMonitoring() {
        isRunning = true
        job = scope.launch {
            val monitor = LogcatMonitor(this@MonitorService)
            monitor.start(
                onCrash = { record -> CrashStore.add(this@MonitorService, record) },
                onError = { msg ->
                    getSystemService(NotificationManager::class.java)
                        .notify(NOTIF_ID, buildNotification("监控异常：$msg"))
                }
            )
            stopSelf()
        }
    }

    private fun stopMonitoring() {
        isRunning = false
        job?.cancel()
        job = null
        LogcatMonitor.killCurrent()
    }

    override fun onDestroy() {
        stopMonitoring()
        super.onDestroy()
    }
}
