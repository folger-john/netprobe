package com.netprobe.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.netprobe.app.data.database.Converters

@Entity(tableName = "scan_results")
@TypeConverters(Converters::class)
data class ScanResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scanType: String, // "port", "network", "ping"
    val target: String, // hostname or IP
    val timestamp: Long = System.currentTimeMillis(),
    val summary: String, // e.g. "5 open ports found"
    val details: String, // JSON string of detailed results
    val duration: Long = 0 // scan duration in ms
)
