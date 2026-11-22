package com.crashcatcher

import org.json.JSONObject

/**
 * 一条崩溃记录。
 */
data class CrashRecord(
    val id: String,
    val time: Long,
    val packageName: String,
    val type: String,
    val message: String,
    val log: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("time", time)
        put("packageName", packageName)
        put("type", type)
        put("message", message)
        put("log", log)
    }

    companion object {
        fun fromJson(o: JSONObject): CrashRecord = CrashRecord(
            id = o.optString("id"),
            time = o.optLong("time"),
            packageName = o.optString("packageName", "未知应用"),
            type = o.optString("type", "崩溃"),
            message = o.optString("message"),
            log = o.optString("log")
        )
    }
}
