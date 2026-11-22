package com.crashcatcher

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.crashcatcher.databinding.ActivityDetailBinding

class DetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.title = "崩溃详情"
        binding.toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val id = intent.getStringExtra("id")
        val record = CrashStore.loadAll(this).firstOrNull { it.id == id }
        if (record == null) {
            finish()
            return
        }

        binding.tvMeta.text =
            "${record.packageName}  ·  ${record.type}  ·  ${CrashStore.formatTime(record.time)}"
        binding.tvLog.text = record.log

        binding.btnShare.setOnClickListener {
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "崩溃记录 ${record.packageName}")
                putExtra(Intent.EXTRA_TEXT, record.log)
            }
            startActivity(Intent.createChooser(share, "分享崩溃记录"))
        }
    }
}
