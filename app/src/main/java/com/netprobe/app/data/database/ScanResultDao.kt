package com.netprobe.app.data.database

import androidx.room.*
import com.netprobe.app.data.model.ScanResult
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanResultDao {
    @Query("SELECT * FROM scan_results ORDER BY timestamp DESC")
    fun getAllResults(): Flow<List<ScanResult>>

    @Query("SELECT * FROM scan_results WHERE scanType = :type ORDER BY timestamp DESC")
    fun getResultsByType(type: String): Flow<List<ScanResult>>

    @Insert
    suspend fun insert(result: ScanResult): Long

    @Delete
    suspend fun delete(result: ScanResult)

    @Query("DELETE FROM scan_results")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM scan_results")
    suspend fun getCount(): Int
}
