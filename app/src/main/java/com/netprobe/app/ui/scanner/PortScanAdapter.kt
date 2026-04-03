package com.netprobe.app.ui.scanner

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.netprobe.app.R
import com.netprobe.app.databinding.ItemPortResultBinding
import com.netprobe.app.scanner.PortResult

class PortScanAdapter :
    ListAdapter<PortResult, PortScanAdapter.PortViewHolder>(PortDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PortViewHolder {
        val binding = ItemPortResultBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PortViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PortViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PortViewHolder(
        private val binding: ItemPortResultBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(result: PortResult) {
            binding.textPort.text = result.port.toString()
            binding.textService.text = result.serviceName

            val tealColor = ContextCompat.getColor(binding.root.context, R.color.teal)
            binding.textStatus.text = "Open"
            binding.textStatus.setTextColor(tealColor)
            binding.textPort.setTextColor(tealColor)
            binding.statusDot.backgroundTintList =
                android.content.res.ColorStateList.valueOf(tealColor)
        }
    }

    private class PortDiffCallback : DiffUtil.ItemCallback<PortResult>() {
        override fun areItemsTheSame(oldItem: PortResult, newItem: PortResult): Boolean {
            return oldItem.port == newItem.port
        }

        override fun areContentsTheSame(oldItem: PortResult, newItem: PortResult): Boolean {
            return oldItem == newItem
        }
    }
}
