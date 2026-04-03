package com.netprobe.app.scanner

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

data class PingResult(
    val host: String,
    val transmitted: Int,
    val received: Int,
    val lossPercent: Float,
    val minRtt: Float,
    val avgRtt: Float,
    val maxRtt: Float,
    val mdevRtt: Float,
    val rawOutput: String,
    val isSuccessful: Boolean,
    val errorMessage: String? = null
)

class PingUtil {

    companion object {

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

        fun isValidHost(host: String): Boolean {
            if (host.isBlank() || host.length > 253) return false
            if (SHELL_METACHARACTERS.containsMatchIn(host)) return false
            return IPV4_REGEX.matches(host) ||
                IPV6_REGEX.matches(host) ||
                HOSTNAME_REGEX.matches(host)
        }

        suspend fun ping(host: String, count: Int = 4, deadlineSeconds: Int = 2): PingResult {
            if (!isValidHost(host)) {
                return PingResult(
                    host = host,
                    transmitted = 0,
                    received = 0,
                    lossPercent = 100f,
                    minRtt = 0f,
                    avgRtt = 0f,
                    maxRtt = 0f,
                    mdevRtt = 0f,
                    rawOutput = "",
                    isSuccessful = false,
                    errorMessage = "Invalid host: contains disallowed characters or invalid format"
                )
            }

            return withContext(Dispatchers.IO) {
                try {
                    val process = Runtime.getRuntime().exec(
                        arrayOf("ping", "-c", count.toString(), "-W", deadlineSeconds.toString(), host)
                    )

                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    val errorReader = BufferedReader(InputStreamReader(process.errorStream))
                    val output = StringBuilder()
                    var line: String?

                    while (reader.readLine().also { line = it } != null) {
                        output.appendLine(line)
                    }

                    val errorOutput = errorReader.readText()
                    if (errorOutput.isNotBlank()) {
                        output.appendLine(errorOutput)
                    }

                    process.waitFor()

                    val rawOutput = output.toString().trim()
                    parsePingOutput(host, rawOutput)
                } catch (e: Exception) {
                    PingResult(
                        host = host,
                        transmitted = 0,
                        received = 0,
                        lossPercent = 100f,
                        minRtt = 0f,
                        avgRtt = 0f,
                        maxRtt = 0f,
                        mdevRtt = 0f,
                        rawOutput = "",
                        isSuccessful = false,
                        errorMessage = e.message ?: "Unknown error"
                    )
                }
            }
        }

        private fun parsePingOutput(host: String, rawOutput: String): PingResult {
            var transmitted = 0
            var received = 0
            var lossPercent = 100f
            var minRtt = 0f
            var avgRtt = 0f
            var maxRtt = 0f
            var mdevRtt = 0f

            // Parse packet statistics: "4 packets transmitted, 4 received, 0% packet loss"
            val statsRegex = Regex(
                "(\\d+)\\s+packets?\\s+transmitted,\\s+(\\d+)\\s+received.*?(\\d+(?:\\.\\d+)?)%\\s+packet\\s+loss"
            )
            val statsMatch = statsRegex.find(rawOutput)
            if (statsMatch != null) {
                transmitted = statsMatch.groupValues[1].toIntOrNull() ?: 0
                received = statsMatch.groupValues[2].toIntOrNull() ?: 0
                lossPercent = statsMatch.groupValues[3].toFloatOrNull() ?: 100f
            }

            // Parse RTT: "rtt min/avg/max/mdev = 1.234/5.678/9.012/3.456 ms"
            val rttRegex = Regex(
                "rtt\\s+min/avg/max/mdev\\s*=\\s*" +
                    "(\\d+(?:\\.\\d+)?)/(\\d+(?:\\.\\d+)?)/(\\d+(?:\\.\\d+)?)/(\\d+(?:\\.\\d+)?)\\s*ms"
            )
            val rttMatch = rttRegex.find(rawOutput)
            if (rttMatch != null) {
                minRtt = rttMatch.groupValues[1].toFloatOrNull() ?: 0f
                avgRtt = rttMatch.groupValues[2].toFloatOrNull() ?: 0f
                maxRtt = rttMatch.groupValues[3].toFloatOrNull() ?: 0f
                mdevRtt = rttMatch.groupValues[4].toFloatOrNull() ?: 0f
            }

            return PingResult(
                host = host,
                transmitted = transmitted,
                received = received,
                lossPercent = lossPercent,
                minRtt = minRtt,
                avgRtt = avgRtt,
                maxRtt = maxRtt,
                mdevRtt = mdevRtt,
                rawOutput = rawOutput,
                isSuccessful = received > 0
            )
        }
    }
}
