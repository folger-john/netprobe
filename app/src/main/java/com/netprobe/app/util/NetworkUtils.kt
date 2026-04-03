package com.netprobe.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL

object NetworkUtils {

    private val HOSTNAME_REGEX = Regex(
        "^([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.)*[a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?$"
    )

    private val IPV4_REGEX = Regex(
        "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    )

    private val IPV6_REGEX = Regex(
        "^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$|" +
            "^::([0-9a-fA-F]{1,4}:){0,6}[0-9a-fA-F]{1,4}$|" +
            "^([0-9a-fA-F]{1,4}:){1,6}:([0-9a-fA-F]{1,4})?$|" +
            "^([0-9a-fA-F]{1,4}:){1,7}:$|" +
            "^::$"
    )

    private val SHELL_METACHARACTERS = Regex("[;&|`$(){}\\[\\]<>!\\\\\"'\\n\\r\\t]")

    /**
     * Returns true if the host is a valid hostname or IP address.
     * Rejects shell metacharacters for security (command injection prevention).
     */
    fun isValidHost(host: String): Boolean {
        if (host.isBlank() || host.length > 253) return false
        if (SHELL_METACHARACTERS.containsMatchIn(host)) return false
        return IPV4_REGEX.matches(host) ||
            IPV6_REGEX.matches(host) ||
            HOSTNAME_REGEX.matches(host)
    }

    /**
     * Gets the device's WiFi IP address.
     * Falls back to iterating NetworkInterfaces if WifiManager is unavailable.
     */
    @Suppress("DEPRECATION")
    fun getDeviceIp(context: Context): String {
        try {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiManager != null) {
                val wifiInfo = wifiManager.connectionInfo
                val ipInt = wifiInfo.ipAddress
                if (ipInt != 0) {
                    return intToIp(ipInt)
                }
            }
        } catch (e: Exception) {
            // Fall through to NetworkInterface approach
        }

        // Fallback: iterate network interfaces
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        return address.hostAddress ?: "0.0.0.0"
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return "0.0.0.0"
    }

    /**
     * Gets the subnet mask. Attempts to read from NetworkInterface prefix length.
     * Falls back to the WiFi DHCP info.
     */
    @Suppress("DEPRECATION")
    fun getSubnetMask(context: Context): String {
        try {
            val deviceIp = getDeviceIp(context)
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                for (interfaceAddress in networkInterface.interfaceAddresses) {
                    val address = interfaceAddress.address
                    if (address is Inet4Address && address.hostAddress == deviceIp) {
                        val prefixLength = interfaceAddress.networkPrefixLength.toInt()
                        return prefixLengthToSubnetMask(prefixLength)
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }

        // Fallback via DHCP info
        try {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val dhcpInfo = wifiManager?.dhcpInfo
            if (dhcpInfo != null) {
                return intToIp(dhcpInfo.netmask)
            }
        } catch (e: Exception) {
            // Ignore
        }
        return "255.255.255.0"
    }

    /**
     * Gets the default gateway address from DHCP info.
     */
    @Suppress("DEPRECATION")
    fun getGateway(context: Context): String {
        try {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val dhcpInfo = wifiManager?.dhcpInfo
            if (dhcpInfo != null && dhcpInfo.gateway != 0) {
                return intToIp(dhcpInfo.gateway)
            }
        } catch (e: Exception) {
            // Ignore
        }
        return "0.0.0.0"
    }

    /**
     * Gets the DNS servers configured on the device.
     * Uses ConnectivityManager on API 23+ and falls back to DHCP info.
     */
    @Suppress("DEPRECATION")
    fun getDnsServers(context: Context): List<String> {
        val dnsServers = mutableListOf<String>()

        // Modern approach via ConnectivityManager (API 23+)
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as? ConnectivityManager
            if (connectivityManager != null) {
                val activeNetwork = connectivityManager.activeNetwork
                if (activeNetwork != null) {
                    val linkProperties = connectivityManager.getLinkProperties(activeNetwork)
                    if (linkProperties != null) {
                        for (dns in linkProperties.dnsServers) {
                            val addr = dns.hostAddress
                            if (addr != null) {
                                dnsServers.add(addr)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Fall through
        }

        // Fallback to DHCP info
        if (dnsServers.isEmpty()) {
            try {
                val wifiManager = context.applicationContext
                    .getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val dhcpInfo = wifiManager?.dhcpInfo
                if (dhcpInfo != null) {
                    if (dhcpInfo.dns1 != 0) dnsServers.add(intToIp(dhcpInfo.dns1))
                    if (dhcpInfo.dns2 != 0) dnsServers.add(intToIp(dhcpInfo.dns2))
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        return dnsServers
    }

    /**
     * Gets the device MAC address.
     * On Android 6+ this may return "02:00:00:00:00:00" due to privacy restrictions.
     */
    fun getMacAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.name.equals("wlan0", ignoreCase = true)) {
                    val macBytes = networkInterface.hardwareAddress ?: continue
                    return macBytes.joinToString(":") { String.format("%02X", it) }
                }
            }

            // Fallback: try any non-loopback interface
            val allInterfaces = NetworkInterface.getNetworkInterfaces()
            while (allInterfaces.hasMoreElements()) {
                val networkInterface = allInterfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                val macBytes = networkInterface.hardwareAddress ?: continue
                if (macBytes.isNotEmpty()) {
                    return macBytes.joinToString(":") { String.format("%02X", it) }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return "02:00:00:00:00:00"
    }

    /**
     * Returns the current network type: "WiFi", "Cellular", or "None".
     */
    fun getNetworkType(context: Context): String {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: return "None"

        val activeNetwork = connectivityManager.activeNetwork ?: return "None"
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            ?: return "None"

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Unknown"
        }
    }

    /**
     * Fetches the device's external/public IP address from api.ipify.org.
     * Must be called from a coroutine.
     */
    suspend fun getExternalIp(): String {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://api.ipify.org")
                val connection = url.openConnection()
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                val reader = BufferedReader(InputStreamReader(connection.getInputStream()))
                val ip = reader.readLine()?.trim() ?: "Unknown"
                reader.close()
                ip
            } catch (e: Exception) {
                "Unknown"
            }
        }
    }

    /**
     * Converts an integer IP (little-endian from Android WifiManager) to a dotted string.
     */
    private fun intToIp(ipInt: Int): String {
        return "${ipInt and 0xFF}" +
            ".${(ipInt shr 8) and 0xFF}" +
            ".${(ipInt shr 16) and 0xFF}" +
            ".${(ipInt shr 24) and 0xFF}"
    }

    /**
     * Converts a CIDR prefix length to a dotted-decimal subnet mask.
     */
    private fun prefixLengthToSubnetMask(prefixLength: Int): String {
        val mask = if (prefixLength == 0) 0 else (-1 shl (32 - prefixLength))
        return "${(mask shr 24) and 0xFF}" +
            ".${(mask shr 16) and 0xFF}" +
            ".${(mask shr 8) and 0xFF}" +
            ".${mask and 0xFF}"
    }
}
