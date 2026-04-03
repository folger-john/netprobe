package com.netprobe.app.ui.tools

import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.getSystemService
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.netprobe.app.R
import com.netprobe.app.data.database.AppDatabase
import com.netprobe.app.data.model.ScanResult
import com.netprobe.app.databinding.FragmentToolsBinding
import com.netprobe.app.scanner.NetworkScanner
import com.netprobe.app.scanner.PingUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL
import java.net.HttpURLConnection

class ToolsFragment : Fragment() {

    private var _binding: FragmentToolsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentToolsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.textPingResult.typeface = Typeface.MONOSPACE
        binding.textWhoisResult.typeface = Typeface.MONOSPACE
        binding.textDeviceInfo.typeface = Typeface.MONOSPACE

        setupPing()
        setupWhois()
        loadDeviceInfo()
    }

    private fun setupPing() {
        binding.btnPing.setOnClickListener {
            val host = binding.editPingHost.text.toString().trim()
            if (host.isBlank()) {
                binding.editPingHost.error = getString(R.string.error_invalid_host)
                binding.textPingResult.text = ""
                return@setOnClickListener
            }
            if (!PingUtil.isValidHost(host)) {
                binding.editPingHost.error = getString(R.string.error_invalid_host)
                return@setOnClickListener
            }

            binding.btnPing.isEnabled = false
            binding.progressPing.visibility = View.VISIBLE
            binding.textPingResult.text = "Pinging $host..."

            lifecycleScope.launch {
                val startTime = System.currentTimeMillis()
                val result = PingUtil.ping(host)
                val duration = System.currentTimeMillis() - startTime

                binding.progressPing.visibility = View.GONE
                binding.btnPing.isEnabled = true

                if (result.isSuccessful) {
                    binding.textPingResult.text = buildString {
                        appendLine("--- Ping $host ---")
                        appendLine("Packets: ${result.transmitted} sent, ${result.received} received")
                        appendLine("Loss:    ${"%.1f".format(result.lossPercent)}%")
                        appendLine()
                        appendLine("RTT (ms):")
                        appendLine("  min   = ${"%.3f".format(result.minRtt)}")
                        appendLine("  avg   = ${"%.3f".format(result.avgRtt)}")
                        appendLine("  max   = ${"%.3f".format(result.maxRtt)}")
                        appendLine("  mdev  = ${"%.3f".format(result.mdevRtt)}")
                        appendLine()
                        appendLine("--- Raw Output ---")
                        append(result.rawOutput)
                    }
                } else {
                    binding.textPingResult.text = "Ping failed: ${result.errorMessage ?: "Host unreachable"}\n\n${result.rawOutput}"
                }

                savePingResult(host, result.isSuccessful, result.avgRtt, duration)
            }
        }
    }

    private fun savePingResult(host: String, success: Boolean, avgRtt: Float, duration: Long) {
        val summary = if (success) {
            "Ping successful - avg RTT: ${"%.1f".format(avgRtt)}ms"
        } else {
            "Ping failed"
        }

        val scanResult = ScanResult(
            scanType = "ping",
            target = host,
            summary = summary,
            details = binding.textPingResult.text.toString(),
            duration = duration
        )

        lifecycleScope.launch {
            AppDatabase.getDatabase(requireContext()).scanResultDao().insert(scanResult)
        }
    }

    private val RDAP_INPUT_REGEX = Regex("^[a-zA-Z0-9.:\\-]+$")

    private fun setupWhois() {
        binding.btnWhois.setOnClickListener {
            val domain = binding.editWhoisDomain.text.toString().trim()
            if (domain.isBlank()) {
                binding.editWhoisDomain.error = "Enter a domain or IP"
                return@setOnClickListener
            }
            if (!RDAP_INPUT_REGEX.matches(domain) || domain.length > 253) {
                binding.editWhoisDomain.error = "Invalid domain or IP format"
                return@setOnClickListener
            }

            binding.btnWhois.isEnabled = false
            binding.progressWhois.visibility = View.VISIBLE
            binding.textWhoisResult.text = "Looking up $domain..."

            lifecycleScope.launch {
                try {
                    val result = fetchRdap(domain)
                    binding.textWhoisResult.text = result
                } catch (e: Exception) {
                    binding.textWhoisResult.text = "Lookup failed: ${e.message}"
                } finally {
                    binding.progressWhois.visibility = View.GONE
                    binding.btnWhois.isEnabled = true
                }
            }
        }
    }

    private suspend fun fetchRdap(query: String): String = withContext(Dispatchers.IO) {
        val isIp = query.matches(Regex("^\\d+\\.\\d+\\.\\d+\\.\\d+$"))
        var urlStr = if (isIp) {
            "https://rdap.org/ip/$query"
        } else {
            "https://rdap.org/domain/$query"
        }

        // Follow redirects manually (RDAP redirects to registry-specific servers)
        var maxRedirects = 5
        while (maxRedirects > 0) {
            val connection = URL(urlStr).openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/rdap+json, application/json")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.instanceFollowRedirects = false

            try {
                val responseCode = connection.responseCode

                if (responseCode in 301..308) {
                    val location = connection.getHeaderField("Location")
                    if (location.isNullOrBlank()) {
                        return@withContext "RDAP redirect with no location"
                    }
                    urlStr = if (location.startsWith("http")) location else {
                        val base = URL(urlStr)
                        URL(base, location).toString()
                    }
                    maxRedirects--
                    connection.disconnect()
                    continue
                }

                if (responseCode != 200) {
                    val errorStream = connection.errorStream
                    val errorBody = errorStream?.bufferedReader()?.readText() ?: ""
                    errorStream?.close()
                    return@withContext "RDAP lookup failed (HTTP $responseCode)\n$errorBody".trim()
                }

                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()
                connection.disconnect()

                return@withContext formatRdapResponse(response, query)
            } catch (e: Exception) {
                connection.disconnect()
                throw e
            }
        }

        "Too many redirects"
    }

    private fun formatRdapResponse(json: String, query: String): String {
        return try {
            val obj = org.json.JSONObject(json)
            buildString {
                appendLine("--- RDAP Lookup: $query ---")
                appendLine()

                if (obj.has("name")) {
                    appendLine("Name:     ${obj.optString("name", "N/A")}")
                }
                if (obj.has("handle")) {
                    appendLine("Handle:   ${obj.optString("handle", "N/A")}")
                }
                if (obj.has("ldhName")) {
                    appendLine("Domain:   ${obj.optString("ldhName", "N/A")}")
                }
                if (obj.has("type")) {
                    appendLine("Type:     ${obj.optString("type", "N/A")}")
                }

                if (obj.has("events")) {
                    val events = obj.getJSONArray("events")
                    appendLine()
                    appendLine("Events:")
                    for (i in 0 until events.length()) {
                        val event = events.getJSONObject(i)
                        val action = event.optString("eventAction", "")
                        val date = event.optString("eventDate", "")
                        appendLine("  $action: $date")
                    }
                }

                if (obj.has("entities")) {
                    val entities = obj.getJSONArray("entities")
                    appendLine()
                    appendLine("Entities:")
                    for (i in 0 until entities.length()) {
                        val entity = entities.getJSONObject(i)
                        val handle = entity.optString("handle", "N/A")
                        val roles = if (entity.has("roles")) {
                            val rolesArr = entity.getJSONArray("roles")
                            (0 until rolesArr.length()).joinToString(", ") { rolesArr.getString(it) }
                        } else "N/A"
                        appendLine("  $handle ($roles)")
                    }
                }

                if (obj.has("nameservers")) {
                    val ns = obj.getJSONArray("nameservers")
                    appendLine()
                    appendLine("Nameservers:")
                    for (i in 0 until ns.length()) {
                        val server = ns.getJSONObject(i)
                        appendLine("  ${server.optString("ldhName", "N/A")}")
                    }
                }

                if (obj.has("status")) {
                    val status = obj.getJSONArray("status")
                    appendLine()
                    appendLine("Status:")
                    for (i in 0 until status.length()) {
                        appendLine("  ${status.getString(i)}")
                    }
                }
            }
        } catch (e: Exception) {
            "Raw response:\n$json"
        }
    }

    private fun loadDeviceInfo() {
        lifecycleScope.launch {
            val info = buildString {
                val localIp = NetworkScanner.getLocalIpAddress() ?: "Unknown"
                appendLine("IP Address:   $localIp")

                val gateway = getGateway()
                appendLine("Gateway:      $gateway")

                val dns = getDnsServers()
                appendLine("DNS Server:   $dns")

                val mac = getMacAddress()
                appendLine("MAC Address:  $mac")

                val networkType = getNetworkType()
                appendLine("Network Type: $networkType")

                appendLine("External IP:  Loading...")
            }
            binding.textDeviceInfo.text = info

            // Fetch external IP in background
            try {
                val externalIp = withContext(Dispatchers.IO) {
                    URL("https://api.ipify.org").readText()
                }
                val current = binding.textDeviceInfo.text.toString()
                binding.textDeviceInfo.text = current.replace("Loading...", externalIp)
            } catch (e: Exception) {
                val current = binding.textDeviceInfo.text.toString()
                binding.textDeviceInfo.text = current.replace("Loading...", "Unavailable")
            }
        }
    }

    private fun getGateway(): String {
        return try {
            val cm = requireContext().getSystemService<ConnectivityManager>() ?: return "Unknown"
            val network = cm.activeNetwork ?: return "Unknown"
            val linkProperties: LinkProperties = cm.getLinkProperties(network) ?: return "Unknown"
            val routes = linkProperties.routes
            val gateway = routes.firstOrNull { it.isDefaultRoute }?.gateway
            gateway?.hostAddress ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun getDnsServers(): String {
        return try {
            val cm = requireContext().getSystemService<ConnectivityManager>() ?: return "Unknown"
            val network = cm.activeNetwork ?: return "Unknown"
            val linkProperties: LinkProperties = cm.getLinkProperties(network) ?: return "Unknown"
            linkProperties.dnsServers.joinToString(", ") { it.hostAddress ?: "?" }
                .ifEmpty { "Unknown" }
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun getMacAddress(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val mac = networkInterface.hardwareAddress ?: continue
                        return mac.joinToString(":") { "%02X".format(it) }
                    }
                }
            }
            "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun getNetworkType(): String {
        return try {
            val cm = requireContext().getSystemService<ConnectivityManager>() ?: return "Unknown"
            val network = cm.activeNetwork ?: return "No connection"
            val caps = cm.getNetworkCapabilities(network) ?: return "Unknown"
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                else -> "Unknown"
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
