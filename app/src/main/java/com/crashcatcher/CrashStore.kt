package com.crashcatcher

import android.content.Context
import org.json.JSONArray
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃记录的本地持久化（存于应用私有目录的 crashes.json）。
 */
object CrashStore {

    private const val FILE_NAME = "crashes.json"
    private const val MAX_RECORDS = 2000
    private val lock = Any()

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun loadAll(context: Context): MutableList<CrashRecord> {
        val list = mutableListOf<CrashRecord>()
        synchronized(lock) {
            val f = file(context)
            if (f.exists()) {
                try {
                    val arr = JSONArray(f.readText())
                    for (i in 0 until arr.length()) {
                        list.add(CrashRecord.fromJson(arr.getJSONObject(i)))
                    }
                } catch (_: Exception) {
                }
            }
        }
        return list
    }

    fun add(context: Context, record: CrashRecord) {
        synchronized(lock) {
            val list = loadAll(context)
            list.add(0, record) // 最新的排在最前面
            while (list.size > MAX_RECORDS) {
                list.removeAt(list.size - 1)
            }
            val arr = JSONArray()
            for (r in list) arr.put(r.toJson())
            try {
                file(context).writeText(arr.toString())
            } catch (_: Exception) {
            }
        }
    }

    fun clear(context: Context) {
        synchronized(lock) {
            val f = file(context)
            if (f.exists()) {
                f.delete()
            }
        }
    }

    fun exportText(context: Context): String {
        val list = loadAll(context)
        val sb = StringBuilder()
        sb.append("崩溃记录导出  共 ").append(list.size).append(" 条\n")
        sb.append("导出时间：").append(formatTime(System.currentTimeMillis())).append("\n")
        sb.append("================================================\n\n")
        for (r in list) {
            sb.append(formatTime(r.time)).append("  [").append(r.type).append("]  ")
                .append(r.packageName).append("\n")
            sb.append(r.log).append("\n")
            sb.append("------------------------------------------------\n\n")
        }
        return sb.toString()
    }

    fun formatTime(t: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(t))
}
