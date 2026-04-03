package com.netprobe.app.scanner

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

data class NetworkDevice(
    val ip: String,
    val hostname: String,
    val isReachable: Boolean,
    val openPorts: List<Int>
)

class NetworkScanner(
    private val timeout: Int = 1000
) {

    companion object {
        private val COMMON_PROBE_PORTS = listOf(80, 443, 22, 8080)

        fun getLocalIpAddress(): String? {
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val networkInterface = interfaces.nextElement()
                    if (networkInterface.isLoopback || !networkInterface.isUp) continue

                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        if (address is java.net.Inet4Address && !address.isLoopbackAddress) {
                            return address.hostAddress
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
            return null
        }

        fun getSubnetPrefix(ip: String): String? {
            val parts = ip.split(".")
            if (parts.size != 4) return null
            return "${parts[0]}.${parts[1]}.${parts[2]}"
        }
    }

    fun scanNetwork(): Flow<NetworkDevice> = channelFlow {
        val localIp = getLocalIpAddress() ?: return@channelFlow
        val subnetPrefix = getSubnetPrefix(localIp) ?: return@channelFlow
        val semaphore = Semaphore(50)

        coroutineScope {
            val jobs = (1..254).map { hostByte ->
                val targetIp = "$subnetPrefix.$hostByte"
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        probeHost(targetIp)
                    }
                }
            }
            for (job in jobs) {
                val device = job.await()
                if (device != null) {
                    send(device)
                }
            }
        }
    }

    fun scanSubnet(subnetPrefix: String): Flow<NetworkDevice> = channelFlow {
        val semaphore = Semaphore(50)

        coroutineScope {
            val jobs = (1..254).map { hostByte ->
                val targetIp = "$subnetPrefix.$hostByte"
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        probeHost(targetIp)
                    }
                }
            }
            for (job in jobs) {
                val device = job.await()
                if (device != null) {
                    send(device)
                }
            }
        }
    }

    private suspend fun probeHost(ip: String): NetworkDevice? {
        return withContext(Dispatchers.IO) {
            try {
                val inetAddress = InetAddress.getByName(ip)
                val isReachable = inetAddress.isReachable(timeout)

                val openPorts = mutableListOf<Int>()
                for (port in COMMON_PROBE_PORTS) {
                    if (isPortOpen(ip, port)) {
                        openPorts.add(port)
                    }
                }

                if (!isReachable && openPorts.isEmpty()) {
                    return@withContext null
                }

                val hostname = try {
                    val resolved = InetAddress.getByName(ip)
                    val canonicalName = resolved.canonicalHostName
                    if (canonicalName != ip) canonicalName else ""
                } catch (e: Exception) {
                    ""
                }

                NetworkDevice(
                    ip = ip,
                    hostname = hostname,
                    isReachable = isReachable,
                    openPorts = openPorts
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun isPortOpen(host: String, port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeout)
                true
            }
        } catch (e: Exception) {
            false
        }
    }
}
