package com.netprobe.app.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.netprobe.app.data.model.ScanResult
import com.netprobe.app.databinding.ItemHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(
    private val onItemClick: (ScanResult) -> Unit
) : ListAdapter<ScanResult, HistoryAdapter.HistoryViewHolder>(HistoryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class HistoryViewHolder(
        private val binding: ItemHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

        fun bind(result: ScanResult) {
            val typeLabel = when (result.scanType) {
                "port" -> "PORT SCAN"
                "network" -> "NETWORK SCAN"
                "ping" -> "PING"
                else -> result.scanType.uppercase()
            }

            val typeIcon = when (result.scanType) {
                "port" -> android.R.drawable.ic_menu_search
                "network" -> android.R.drawable.ic_menu_mapmode
                "ping" -> android.R.drawable.ic_menu_manage
                else -> android.R.drawable.ic_menu_search
            }

            binding.textScanType.text = typeLabel
            binding.imageScanType.setImageResource(typeIcon)
            binding.textTarget.text = result.target
            binding.textSummary.text = result.summary
            binding.textTimestamp.text = dateFormat.format(Date(result.timestamp))

            val durationSec = result.duration / 1000.0
            binding.textDuration.text = "${"%.1f".format(durationSec)}s"

            binding.root.setOnClickListener {
                onItemClick(result)
            }
        }
    }

    private class HistoryDiffCallback : DiffUtil.ItemCallback<ScanResult>() {
        override fun areItemsTheSame(oldItem: ScanResult, newItem: ScanResult): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ScanResult, newItem: ScanResult): Boolean {
            return oldItem == newItem
        }
    }
}
