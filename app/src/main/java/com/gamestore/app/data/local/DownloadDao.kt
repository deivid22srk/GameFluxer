package com.gamestore.app.data.local

import androidx.room.*
import com.gamestore.app.data.model.Download
import com.gamestore.app.data.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun getAllDownloads(): Flow<List<Download>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getDownloadById(id: String): Download?

    @Query("SELECT * FROM downloads WHERE gameId = :gameId")
    suspend fun getDownloadByGameId(gameId: String): Download?

    @Query("SELECT * FROM downloads WHERE gameId = :gameId LIMIT 1")
    fun observeDownloadByGameId(gameId: String): Flow<Download?>

    @Query("SELECT * FROM downloads WHERE status IN (:statuses)")
    fun getDownloadsByStatus(statuses: List<DownloadStatus>): Flow<List<Download>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: Download)

    @Update
    suspend fun update(download: Download)

    @Query("UPDATE downloads SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: DownloadStatus)

    @Query("UPDATE downloads SET downloadedBytes = :downloadedBytes, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateProgress(id: String, downloadedBytes: Long, updatedAt: Long)

    @Delete
    suspend fun delete(download: Download)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM downloads WHERE status = :status")
    suspend fun deleteByStatus(status: DownloadStatus)
}
