package com.netprobe.app.ui.scanner

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
import com.netprobe.app.databinding.FragmentPortScannerBinding
import com.netprobe.app.scanner.PingUtil
import com.netprobe.app.scanner.PortResult
import com.netprobe.app.scanner.PortScanner
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class PortScannerFragment : Fragment() {

    private var _binding: FragmentPortScannerBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: PortScanAdapter
    private var scanJob: Job? = null
    private val results = mutableListOf<PortResult>()
    private val openResults = mutableListOf<PortResult>()
    private var scannedCount = 0

    companion object {
        private val COMMON_PORTS = listOf(21, 22, 23, 25, 53, 80, 110, 143, 443, 993, 995, 3306, 3389, 5432, 8080, 8443)
        private val WEB_PORTS = listOf(80, 443, 8080, 8443, 3000, 5000, 8000, 8888)
        private val DATABASE_PORTS = listOf(3306, 5432, 27017, 6379, 1433, 1521, 5984, 9200)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPortScannerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = PortScanAdapter()
        binding.recyclerResults.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerResults.adapter = adapter

        setupPresetButtons()
        setupScanButton()
    }

    private fun setupPresetButtons() {
        binding.btnPresetCommon.setOnClickListener {
            binding.editStartPort.setText("")
            binding.editEndPort.setText("")
            binding.tilStartPort.hint = "Common (${COMMON_PORTS.size} ports)"
            binding.tilEndPort.hint = COMMON_PORTS.joinToString(", ")
            binding.editStartPort.tag = COMMON_PORTS
        }

        binding.btnPresetWeb.setOnClickListener {
            binding.editStartPort.setText("")
            binding.editEndPort.setText("")
            binding.tilStartPort.hint = "Web (${WEB_PORTS.size} ports)"
            binding.tilEndPort.hint = WEB_PORTS.joinToString(", ")
            binding.editStartPort.tag = WEB_PORTS
        }

        binding.btnPresetDatabase.setOnClickListener {
            binding.editStartPort.setText("")
            binding.editEndPort.setText("")
            binding.tilStartPort.hint = "DB (${DATABASE_PORTS.size} ports)"
            binding.tilEndPort.hint = DATABASE_PORTS.joinToString(", ")
            binding.editStartPort.tag = DATABASE_PORTS
        }

        binding.btnPresetAll.setOnClickListener {
            binding.editStartPort.setText("1")
            binding.editEndPort.setText("1024")
            binding.tilStartPort.hint = getString(R.string.hint_start_port)
            binding.tilEndPort.hint = getString(R.string.hint_end_port)
            binding.editStartPort.tag = null
        }
    }

    private fun setupScanButton() {
        binding.btnScan.setOnClickListener {
            val host = binding.editHost.text.toString().trim()
            if (!PingUtil.isValidHost(host)) {
                binding.editHost.error = getString(R.string.error_invalid_host)
                return@setOnClickListener
            }

            // Validate port range when not using presets
            val presetPorts = binding.editStartPort.tag as? List<*>
            if (presetPorts == null) {
                val startPort = binding.editStartPort.text.toString().toIntOrNull()
                val endPort = binding.editEndPort.text.toString().toIntOrNull()
                if (startPort == null || endPort == null || startPort < 1 || endPort > 65535 || startPort > endPort) {
                    binding.editStartPort.error = getString(R.string.error_invalid_port)
                    return@setOnClickListener
                }
            }

            startScan(host)
        }

        binding.btnStop.setOnClickListener {
            cancelScan()
        }
    }

    private fun startScan(host: String) {
        results.clear()
        openResults.clear()
        scannedCount = 0
        adapter.submitList(emptyList())
        binding.textSummary.visibility = View.GONE

        val presetPorts = binding.editStartPort.tag as? List<*>
        val startTime = System.currentTimeMillis()

        binding.progressBar.visibility = View.VISIBLE
        binding.btnScan.visibility = View.GONE
        binding.btnStop.visibility = View.VISIBLE
        binding.textStatus.text = getString(R.string.status_scanning, host, 0, 0)

        scanJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                if (presetPorts != null) {
                    @Suppress("UNCHECKED_CAST")
                    val ports = presetPorts as List<Int>
                    val totalPorts = ports.size
                    for (port in ports) {
                        val scanner = PortScanner(host, port, port)
                        scanner.scan().collect { result ->
                            scannedCount++
                            results.add(result)
                            if (result.isOpen) {
                                openResults.add(result)
                                adapter.submitList(openResults.toList())
                            }
                            _binding?.textStatus?.text = getString(
                                R.string.status_scanning, host, scannedCount, totalPorts
                            )
                        }
                    }
                } else {
                    val startPort = _binding?.editStartPort?.text?.toString()?.toIntOrNull() ?: 1
                    val endPort = _binding?.editEndPort?.text?.toString()?.toIntOrNull() ?: 1024
                    val totalPorts = endPort - startPort + 1
                    val scanner = PortScanner(host, startPort, endPort)
                    scanner.scanConcurrent().collect { result ->
                        scannedCount++
                        results.add(result)
                        if (result.isOpen) {
                            openResults.add(result)
                            adapter.submitList(openResults.toList())
                        }
                        _binding?.textStatus?.text = getString(
                            R.string.status_scanning, host, scannedCount, totalPorts
                        )
                    }
                }

                val duration = System.currentTimeMillis() - startTime
                val openCount = results.count { it.isOpen }
                val closedCount = results.size - openCount
                val seconds = duration / 1000.0

                _binding?.let { b ->
                    b.textStatus.text = getString(R.string.status_complete, openCount, closedCount)
                    b.textSummary.visibility = View.VISIBLE
                    val portWord = if (openCount == 1) "port" else "ports"
                    b.textSummary.text = "$openCount open $portWord found in ${"%.1f".format(seconds)}s"
                }

                saveScanResult(host, openCount, duration)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    _binding?.textStatus?.text = "Scan cancelled"
                } else {
                    _binding?.textStatus?.text = "Error: ${e.message}"
                }
            } finally {
                resetScanUi()
            }
        }
    }

    private fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
    }

    private fun resetScanUi() {
        val binding = _binding ?: return
        binding.progressBar.visibility = View.GONE
        binding.btnScan.visibility = View.VISIBLE
        binding.btnStop.visibility = View.GONE
    }

    private fun saveScanResult(host: String, openCount: Int, duration: Long) {
        val openPorts = results.filter { it.isOpen }
        val detailsJson = JSONArray().apply {
            for (result in openPorts) {
                put(JSONObject().apply {
                    put("port", result.port)
                    put("service", result.serviceName)
                    put("open", result.isOpen)
                })
            }
        }.toString()

        val portWord = if (openCount == 1) "port" else "ports"
        val scanResult = ScanResult(
            scanType = "port",
            target = host,
            summary = "$openCount open $portWord found (${results.size} scanned)",
            details = detailsJson,
            duration = duration
        )

        lifecycleScope.launch {
            AppDatabase.getDatabase(requireContext()).scanResultDao().insert(scanResult)
        }
    }

    override fun onDestroyView() {
        cancelScan()
        _binding = null
        super.onDestroyView()
    }
}
