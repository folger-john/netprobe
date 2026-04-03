package com.netprobe.app.scanner

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

data class PortResult(
    val port: Int,
    val isOpen: Boolean,
    val serviceName: String
)

class PortScanner(
    private val host: String,
    private val startPort: Int,
    private val endPort: Int,
    private val timeout: Int = 1000
) {

    companion object {
        val WELL_KNOWN_PORTS: Map<Int, String> = mapOf(
            20 to "FTP Data",
            21 to "FTP",
            22 to "SSH",
            23 to "Telnet",
            25 to "SMTP",
            53 to "DNS",
            67 to "DHCP Server",
            68 to "DHCP Client",
            69 to "TFTP",
            80 to "HTTP",
            110 to "POP3",
            111 to "RPCBind",
            119 to "NNTP",
            123 to "NTP",
            135 to "MS RPC",
            137 to "NetBIOS Name",
            138 to "NetBIOS Datagram",
            139 to "NetBIOS Session",
            143 to "IMAP",
            161 to "SNMP",
            162 to "SNMP Trap",
            389 to "LDAP",
            443 to "HTTPS",
            445 to "SMB",
            465 to "SMTPS",
            514 to "Syslog",
            587 to "SMTP Submission",
            636 to "LDAPS",
            993 to "IMAPS",
            995 to "POP3S",
            1080 to "SOCKS Proxy",
            1433 to "MSSQL",
            1521 to "Oracle DB",
            1723 to "PPTP",
            2049 to "NFS",
            3306 to "MySQL",
            3389 to "RDP",
            5432 to "PostgreSQL",
            5900 to "VNC",
            5984 to "CouchDB",
            6379 to "Redis",
            6443 to "Kubernetes API",
            8080 to "HTTP Proxy",
            8443 to "HTTPS Alt",
            8888 to "HTTP Alt",
            9090 to "Prometheus",
            9200 to "Elasticsearch",
            11211 to "Memcached",
            27017 to "MongoDB",
            50000 to "SAP"
        )

        fun getServiceName(port: Int): String {
            return WELL_KNOWN_PORTS[port] ?: "Unknown"
        }
    }

    fun scan(): Flow<PortResult> = channelFlow {
        val semaphore = Semaphore(100)

        coroutineScope {
            for (port in startPort..endPort) {
                val deferred = async(Dispatchers.IO) {
                    semaphore.withPermit {
                        scanPort(port)
                    }
                }
                val result = deferred.await()
                send(result)
            }
        }
    }

    fun scanConcurrent(): Flow<PortResult> = channelFlow {
        val semaphore = Semaphore(100)

        coroutineScope {
            val jobs = (startPort..endPort).map { port ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        scanPort(port)
                    }
                }
            }
            for (job in jobs) {
                send(job.await())
            }
        }
    }

    private suspend fun scanPort(port: Int): PortResult {
        return withContext(Dispatchers.IO) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), timeout)
                    PortResult(
                        port = port,
                        isOpen = true,
                        serviceName = getServiceName(port)
                    )
                }
            } catch (e: Exception) {
                PortResult(
                    port = port,
                    isOpen = false,
                    serviceName = getServiceName(port)
                )
            }
        }
    }
}
