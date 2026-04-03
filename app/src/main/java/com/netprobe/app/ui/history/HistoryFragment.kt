package com.netprobe.app.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.netprobe.app.R
import com.netprobe.app.data.database.AppDatabase
import com.netprobe.app.data.model.ScanResult
import com.netprobe.app.databinding.FragmentHistoryBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: HistoryAdapter
    private val dao by lazy {
        AppDatabase.getDatabase(requireContext()).scanResultDao()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = HistoryAdapter { scanResult ->
            showDetailDialog(scanResult)
        }

        binding.recyclerHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerHistory.adapter = adapter

        setupSwipeToDelete()

        binding.fabClearHistory.setOnClickListener {
            showClearConfirmation()
        }

        observeHistory()
    }

    private fun observeHistory() {
        lifecycleScope.launch {
            dao.getAllResults().collect { results ->
                adapter.submitList(results)
                binding.textEmptyState.visibility =
                    if (results.isEmpty()) View.VISIBLE else View.GONE
                binding.recyclerHistory.visibility =
                    if (results.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    private fun setupSwipeToDelete() {
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val item = adapter.currentList[position]
                lifecycleScope.launch {
                    dao.delete(item)
                }
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(binding.recyclerHistory)
    }

    private fun showClearConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.clear_history))
            .setMessage(getString(R.string.confirm_clear_history))
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                lifecycleScope.launch {
                    dao.deleteAll()
                }
            }
            .setNegativeButton(getString(R.string.no), null)
            .show()
    }

    private fun showDetailDialog(scanResult: ScanResult) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val timestamp = dateFormat.format(Date(scanResult.timestamp))
        val durationSec = scanResult.duration / 1000.0

        val detailText = buildString {
            appendLine("Type:     ${scanResult.scanType.uppercase()}")
            appendLine("Target:   ${scanResult.target}")
            appendLine("Time:     $timestamp")
            appendLine("Duration: ${"%.1f".format(durationSec)}s")
            appendLine("Summary:  ${scanResult.summary}")
            appendLine()
            appendLine("--- Details ---")
            appendLine()
            append(formatDetails(scanResult.scanType, scanResult.details))
        }

        AlertDialog.Builder(requireContext())
            .setTitle("${scanResult.scanType.uppercase()} Scan Result")
            .setMessage(detailText)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun formatDetails(scanType: String, details: String): String {
        return try {
            when (scanType) {
                "port" -> {
                    val arr = org.json.JSONArray(details)
                    buildString {
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            val port = obj.getInt("port")
                            val service = obj.getString("service")
                            appendLine("  Port $port ($service) - Open")
                        }
                        if (arr.length() == 0) append("  No open ports")
                    }
                }
                "network" -> {
                    val arr = org.json.JSONArray(details)
                    buildString {
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            val ip = obj.getString("ip")
                            val hostname = obj.optString("hostname", "")
                            val label = if (hostname.isNotEmpty()) "$ip ($hostname)" else ip
                            appendLine("  $label")
                        }
                    }
                }
                else -> details
            }
        } catch (e: Exception) {
            details
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
