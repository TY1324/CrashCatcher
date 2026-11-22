package com.crashcatcher

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.crashcatcher.databinding.ItemCrashBinding

class CrashAdapter(private val onClick: (CrashRecord) -> Unit) :
    RecyclerView.Adapter<CrashAdapter.VH>() {

    private val items = mutableListOf<CrashRecord>()

    fun submit(list: List<CrashRecord>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(val binding: ItemCrashBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemCrashBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = items[position]
        holder.binding.tvPkg.text = r.packageName
        holder.binding.tvType.text = r.type
        holder.binding.tvTime.text = CrashStore.formatTime(r.time)
        holder.binding.tvMsg.text = r.message.take(150)
        holder.binding.root.setOnClickListener { onClick(r) }
    }
}
