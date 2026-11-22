package com.crashcatcher

import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 通过执行 `logcat` 读取系统日志，并用启发式规则识别崩溃：
 *  - Java 崩溃：tag=AndroidRuntime 且包含 "FATAL EXCEPTION"
 *  - 原生崩溃：tag=libc 且包含 "Fatal signal"
 *  - ANR：tag=ActivityManager 且包含 "ANR in "
 *
 * 注意：读取其它 App 的日志需要 READ_LOGS 权限（需 ADB 授予）。
 */
class LogcatMonitor(private val context: android.content.Context) {

    companion object {
        private var currentProcess: Process? = null

        private val CONTINUATION_TAGS = setOf(
            "AndroidRuntime", "libc", "DEBUG", "ActivityManager", "System.err", "System.out"
        )

        fun killCurrent() {
            try {
                currentProcess?.destroy()
            } catch (_: Exception) {
            }
            currentProcess = null
        }

        private const val MAX_LINES = 400
    }

    private val regex = Regex(
        """^\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3}\s+(\d+)\s+(\d+)\s+([VDIWEF])\s+(.*?):\s(.*)$"""
    )

    private class Building(val type: String, val pid: Int, var lastTimeMs: Long) {
        val lines = mutableListOf<String>()
        var packageName: String = "未知应用"
        var message: String = ""
    }

    fun start(onCrash: (CrashRecord) -> Unit, onError: (String) -> Unit) {
        val cmd = arrayOf("logcat", "-v", "threadtime", "-b", "main", "-b", "system", "-b", "crash")
        val process = try {
            ProcessBuilder(*cmd).redirectErrorStream(true).start()
        } catch (e: Exception) {
            onError("无法启动 logcat：${e.message}")
            return
        }
        currentProcess = process
        var current: Building? = null

        fun finalizeCurrent() {
            val c = current ?: return
            if (c.lines.isNotEmpty()) {
                val msg = c.message.ifBlank { c.lines.first() }
                val record = CrashRecord(
                    id = "${System.currentTimeMillis()}-${(0..9999).random()}",
                    time = System.currentTimeMillis(),
                    packageName = c.packageName,
                    type = c.type,
                    message = msg.take(300),
                    log = c.lines.joinToString("\n")
                )
                onCrash(record)
            }
            current = null
        }

        try {
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            while (true) {
                val line = reader.readLine() ?: break
                val m = regex.find(line) ?: continue
                val pid = m.groupValues[1].toIntOrNull() ?: -1
                val tag = m.groupValues[4].trim()
                val msg = m.groupValues[5]
                val now = System.currentTimeMillis()

                val startType = when {
                    tag == "AndroidRuntime" && msg.contains("FATAL EXCEPTION") -> "Java崩溃"
                    tag == "libc" && msg.contains("Fatal signal") -> "原生崩溃"
                    tag == "ActivityManager" && msg.contains("ANR in ") -> "ANR"
                    else -> null
                }

                if (startType != null) {
                    finalizeCurrent()
                    val b = Building(startType, pid, now)
                    b.lines.add(line)
                    b.message = msg
                    if (startType == "ANR") {
                        b.packageName = msg.substringAfter("ANR in ")
                            .substringBefore(" ").substringBefore(",").ifBlank { "未知应用" }
                    }
                    current = b
                } else {
                    val c = current
                    if (c != null) {
                        val samePid = pid == c.pid
                        val contTag = tag in CONTINUATION_TAGS
                        val timeClose = now - c.lastTimeMs <= 5000
                        if ((samePid || contTag) && timeClose && c.lines.size < MAX_LINES) {
                            c.lines.add(line)
                            c.lastTimeMs = now
                            val pm = msg.indexOf("Process: ")
                            if (pm >= 0) {
                                val p = msg.substring(pm + "Process: ".length)
                                    .substringBefore(",").trim()
                                if (p.isNotEmpty()) c.packageName = p
                            }
                            if (c.message.isBlank()) c.message = msg
                        } else {
                            finalizeCurrent()
                        }
                    }
                }
            }
            finalizeCurrent()
            onError("logcat 已结束")
        } catch (e: Exception) {
            finalizeCurrent()
            onError("读取日志失败：${e.message}")
        } finally {
            try {
                process.destroy()
            } catch (_: Exception) {
            }
            currentProcess = null
        }
    }
}
