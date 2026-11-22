package com.crashcatcher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.crashcatcher.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: CrashAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        adapter = CrashAdapter { record ->
            startActivity(Intent(this, DetailActivity::class.java).putExtra("id", record.id))
        }
        binding.rvCrashes.layoutManager = LinearLayoutManager(this)
        binding.rvCrashes.adapter = adapter

        binding.btnToggle.setOnClickListener {
            if (MonitorService.isRunning) {
                MonitorService.stop(this)
                Toast.makeText(this, "已停止日志监控", Toast.LENGTH_SHORT).show()
            } else {
                ensureNotifyPermission()
                MonitorService.start(this)
                Toast.makeText(this, "已尝试启动日志监控（需 ADB 授权）", Toast.LENGTH_SHORT).show()
            }
            binding.root.postDelayed({ refreshUi() }, 400)
        }

        binding.btnRefresh.setOnClickListener { refreshUi() }

        binding.btnClear.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("清空记录")
                .setMessage("确定删除全部崩溃记录？")
                .setPositiveButton("删除") { _, _ ->
                    CrashStore.clear(this)
                    refreshUi()
                }
                .setNegativeButton("取消", null)
                .show()
        }

        binding.btnExport.setOnClickListener { exportAll() }
        binding.tvPermission.setOnClickListener { showModeDialog() }

        ensureNotifyPermission()
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { it.contains("com.crashcatcher") }
    }

    private fun refreshUi() {
        val list = CrashStore.loadAll(this)
        adapter.submit(list)
        binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE

        val logGranted = ContextCompat.checkSelfPermission(
            this, "android.permission.READ_LOGS"
        ) == PackageManager.PERMISSION_GRANTED
        val accEnabled = isAccessibilityEnabled()

        binding.tvPermission.text =
            "无障碍检测：" + (if (accEnabled) "已开启 ✓" else "未开启（点这里开启）") +
                "\n日志权限：" + (if (logGranted) "已授权 ✓" else "未授权（可选）")

        binding.tvStatus.text = when {
            accEnabled && MonitorService.isRunning -> "状态：无障碍 + 日志 双模式"
            accEnabled -> "状态：无障碍检测中"
            MonitorService.isRunning -> "状态：日志监控中"
            else -> "状态：未监控"
        }
        binding.btnToggle.text = if (MonitorService.isRunning) "停止日志监控" else "开始日志监控"
        binding.tvCount.text = "共 ${list.size} 条记录"
    }

    private fun showModeDialog() {
        AlertDialog.Builder(this)
            .setTitle("选择检测方式")
            .setMessage(
                "【推荐】无障碍模式：不用电脑，去设置里打开开关即可，能记录哪个 App 崩溃 / 无响应。\n\n" +
                    "日志模式：需要电脑用 ADB 授权，能记录完整堆栈，不方便可以忽略。"
            )
            .setPositiveButton("去开启无障碍") { _, _ ->
                try {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    Toast.makeText(this, "找到「崩溃捕手」并打开开关", Toast.LENGTH_LONG).show()
                } catch (_: Exception) {
                    Toast.makeText(this, "请手动进入：设置 → 辅助功能 → 无障碍", Toast.LENGTH_LONG).show()
                }
            }
            .setNeutralButton("查看日志模式") { _, _ -> showAdbHelp() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun ensureNotifyPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100
            )
        }
    }

    private fun showAdbHelp() {
        val cmd = "adb shell pm grant $packageName android.permission.READ_LOGS"
        AlertDialog.Builder(this)
            .setTitle("日志模式（需要电脑）")
            .setMessage(
                "如果以后有电脑，可以执行下面命令开启日志模式，能记录完整堆栈：\n\n" + cmd +
                    "\n\n没有电脑就用无障碍模式，足够日常使用。"
            )
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun exportAll() {
        val text = CrashStore.exportText(this)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "崩溃记录导出")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "导出 / 分享崩溃记录"))
    }
}
