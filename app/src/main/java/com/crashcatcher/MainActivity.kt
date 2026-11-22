package com.crashcatcher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
                Toast.makeText(this, "已停止监控", Toast.LENGTH_SHORT).show()
            } else {
                ensureNotifyPermission()
                MonitorService.start(this)
                Toast.makeText(this, "开始监控（若长时间无记录，请检查日志权限）", Toast.LENGTH_SHORT).show()
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
        binding.tvPermission.setOnClickListener { showPermissionHelp() }

        ensureNotifyPermission()
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun refreshUi() {
        val list = CrashStore.loadAll(this)
        adapter.submit(list)
        binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE

        val granted = ContextCompat.checkSelfPermission(
            this, "android.permission.READ_LOGS"
        ) == PackageManager.PERMISSION_GRANTED

        binding.tvPermission.text = if (granted)
            "日志权限：已授权 ✓（点击查看说明）"
        else
            "日志权限：未授权 ✗（点击查看如何授权）"

        binding.tvStatus.text = if (MonitorService.isRunning) "状态：正在监控" else "状态：未监控"
        binding.btnToggle.text = if (MonitorService.isRunning) "停止监控" else "开始监控"
        binding.tvCount.text = "共 ${list.size} 条记录"
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

    private fun showPermissionHelp() {
        val cmd = "adb shell pm grant $packageName android.permission.READ_LOGS"
        AlertDialog.Builder(this)
            .setTitle("如何授权读取日志")
            .setMessage(
                "读取其它 App 的崩溃信息需要「读取日志 (READ_LOGS)」权限，系统不允许在应用内直接申请，需用电脑执行一次命令：\n\n" +
                    "1. 手机开启「开发者选项」→「USB 调试」，用数据线连接电脑；\n" +
                    "2. 电脑安装 adb；\n" +
                    "3. 在电脑终端执行：\n\n" +
                    cmd + "\n\n" +
                    "授权成功后回到本页即可看到「已授权」。\n\n" +
                    "提示：若无法使用电脑，请参考项目 README 中其它方案。"
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
