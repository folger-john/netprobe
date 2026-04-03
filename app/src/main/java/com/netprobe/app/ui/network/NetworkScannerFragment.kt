package com.netprobe.app.ui.network

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.netprobe.app.R
import com.netprobe.app.data.database.AppDatabase
import com.netprobe.app.data.model.ScanResult
import com.netprobe.app.databinding.FragmentNetworkScannerBinding
import com.netprobe.app.scanner.NetworkDevice
import com.netprobe.app.scanner.NetworkScanner
import com.netprobe.app.util.NetworkUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class NetworkScannerFragment : Fragment() {

    private var _binding: FragmentNetworkScannerBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: DeviceAdapter
    private var scanJob: Job? = null
    private val devices = mutableListOf<NetworkDevice>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNetworkScannerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = DeviceAdapter()
        binding.recyclerDevices.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerDevices.adapter = adapter

        loadDeviceInfo()

        binding.btnScanNetwork.setOnClickListener {
            startNetworkScan()
        }
    }

    private fun loadDeviceInfo() {
        val localIp = NetworkUtils.getDeviceIp(requireContext())
        binding.textLocalIp.text = if (localIp == "0.0.0.0") "Unknown" else localIp

        val subnet = NetworkUtils.getSubnetMask(requireContext())
        binding.tvSubnet.text = subnet

        val gateway = NetworkUtils.getGateway(requireContext())
        binding.tvGateway.text = if (gateway == "0.0.0.0") "Unknown" else gateway
    }

    private fun startNetworkScan() {
        devices.clear()
        adapter.submitList(emptyList())

        val startTime = System.currentTimeMillis()
        binding.progressBar.visibility = View.VISIBLE
        binding.btnScanNetwork.isEnabled = false
        binding.textStatus.text = getString(R.string.status_discovering, 0)

        scanJob = lifecycleScope.launch {
            try {
                val scanner = NetworkScanner()
                scanner.scanNetwork().collect { device ->
                    devices.add(device)
                    adapter.submitList(devices.toList())
                    binding.textStatus.text = getString(R.string.status_discovering, devices.size)
                    binding.textDeviceCount.text = "${devices.size}"
                }

                val duration = System.currentTimeMillis() - startTime
                val deviceWord = if (devices.size == 1) "device" else "devices"
                binding.textStatus.text = "Found ${devices.size} $deviceWord"

                saveScanResult(duration)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    binding.textStatus.text = "Scan cancelled"
                } else {
                    binding.textStatus.text = "Error: ${e.message}"
                }
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.btnScanNetwork.isEnabled = true
            }
        }
    }

    private fun saveScanResult(duration: Long) {
        val localIp = NetworkScanner.getLocalIpAddress() ?: "unknown"
        val subnet = NetworkScanner.getSubnetPrefix(localIp) ?: "unknown"

        val detailsJson = JSONArray().apply {
            for (device in devices) {
                put(JSONObject().apply {
                    put("ip", device.ip)
                    put("hostname", device.hostname)
                    put("reachable", device.isReachable)
                    put("openPorts", JSONArray(device.openPorts))
                })
            }
        }.toString()

        val deviceWord = if (devices.size == 1) "device" else "devices"
        val scanResult = ScanResult(
            scanType = "network",
            target = "$subnet.0/24",
            summary = "${devices.size} $deviceWord found",
            details = detailsJson,
            duration = duration
        )

        lifecycleScope.launch {
            AppDatabase.getDatabase(requireContext()).scanResultDao().insert(scanResult)
        }
    }

    override fun onDestroyView() {
        scanJob?.cancel()
        _binding = null
        super.onDestroyView()
    }
}
