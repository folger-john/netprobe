package com.netprobe.app.ui.network

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.netprobe.app.databinding.ItemNetworkDeviceBinding
import com.netprobe.app.scanner.NetworkDevice
import com.netprobe.app.scanner.PortScanner

class DeviceAdapter :
    ListAdapter<NetworkDevice, DeviceAdapter.DeviceViewHolder>(DeviceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemNetworkDeviceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DeviceViewHolder(
        private val binding: ItemNetworkDeviceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(device: NetworkDevice) {
            binding.textIp.text = device.ip
            binding.textHostname.text = device.hostname.ifEmpty { "Unknown" }
            binding.textOpenPorts.text = if (device.openPorts.isEmpty()) {
                "No open ports detected"
            } else {
                device.openPorts.joinToString(", ") { port ->
                    "$port (${PortScanner.getServiceName(port)})"
                }
            }
        }
    }

    private class DeviceDiffCallback : DiffUtil.ItemCallback<NetworkDevice>() {
        override fun areItemsTheSame(oldItem: NetworkDevice, newItem: NetworkDevice): Boolean {
            return oldItem.ip == newItem.ip
        }

        override fun areContentsTheSame(oldItem: NetworkDevice, newItem: NetworkDevice): Boolean {
            return oldItem == newItem
        }
    }
}
