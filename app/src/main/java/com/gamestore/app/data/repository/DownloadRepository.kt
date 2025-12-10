package com.gamestore.app.data.repository

import com.gamestore.app.data.local.DownloadDao
import com.gamestore.app.data.model.Download
import com.gamestore.app.data.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

class DownloadRepository(private val downloadDao: DownloadDao) {
    
    fun getAllDownloads(): Flow<List<Download>> = downloadDao.getAllDownloads()

    fun getDownloadsByStatus(statuses: List<DownloadStatus>): Flow<List<Download>> = 
        downloadDao.getDownloadsByStatus(statuses)

    suspend fun getDownloadById(id: String): Download? = downloadDao.getDownloadById(id)

    suspend fun getDownloadByGameId(gameId: String): Download? = 
        downloadDao.getDownloadByGameId(gameId)

    fun observeDownloadByGameId(gameId: String): Flow<Download?> = 
        downloadDao.observeDownloadByGameId(gameId)

    suspend fun insertDownload(download: Download) = downloadDao.insert(download)

    suspend fun updateDownload(download: Download) = downloadDao.update(download)

    suspend fun updateStatus(id: String, status: DownloadStatus) = 
        downloadDao.updateStatus(id, status)

    suspend fun updateProgress(id: String, downloadedBytes: Long) = 
        downloadDao.updateProgress(id, downloadedBytes, System.currentTimeMillis())

    suspend fun deleteDownload(download: Download) = downloadDao.delete(download)

    suspend fun deleteDownloadById(id: String) = downloadDao.deleteById(id)

    suspend fun deleteByStatus(status: DownloadStatus) = downloadDao.deleteByStatus(status)
}
