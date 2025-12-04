package com.gamestore.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gamestore.app.data.local.GameDatabase
import com.gamestore.app.data.model.Download
import com.gamestore.app.data.model.DownloadStatus
import com.gamestore.app.data.model.Game
import com.gamestore.app.data.preferences.PreferencesManager
import com.gamestore.app.data.repository.DownloadRepository
import com.gamestore.app.service.DownloadService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.*

class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val downloadRepository = DownloadRepository(
        GameDatabase.getDatabase(application).downloadDao()
    )
    
    private val preferencesManager = PreferencesManager(application)

    val allDownloads: StateFlow<List<Download>> = downloadRepository.getAllDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeDownloads: StateFlow<List<Download>> = downloadRepository.getDownloadsByStatus(
        listOf(DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED)
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloadFolder: StateFlow<String?> = preferencesManager.downloadFolder
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun getDownloadForGame(gameId: String): Flow<Download?> = flow {
        emit(downloadRepository.getDownloadByGameId(gameId))
    }.combine(allDownloads) { _, downloads ->
        downloads.find { it.gameId == gameId }
    }

    fun startDownload(game: Game) {
        viewModelScope.launch {
            val existingDownload = downloadRepository.getDownloadByGameId(game.id)
            
            if (existingDownload != null) {
                if (existingDownload.status == DownloadStatus.PAUSED ||
                    existingDownload.status == DownloadStatus.FAILED) {
                    DownloadService.resumeDownload(getApplication(), existingDownload.id)
                }
                return@launch
            }

            val folder = downloadFolder.value ?: getDefaultDownloadFolder()
            val fileName = sanitizeFileName(game.name) + "_" + game.version + ".apk"
            val filePath = File(folder, fileName).absolutePath

            val download = Download(
                id = UUID.randomUUID().toString(),
                gameId = game.id,
                gameName = game.name,
                gameIconUrl = game.iconUrl,
                url = game.downloadUrl,
                filePath = filePath,
                status = DownloadStatus.QUEUED
            )

            downloadRepository.insertDownload(download)
            DownloadService.startDownload(getApplication(), download.id)
        }
    }

    fun pauseDownload(downloadId: String) {
        DownloadService.pauseDownload(getApplication(), downloadId)
    }

    fun resumeDownload(downloadId: String) {
        DownloadService.resumeDownload(getApplication(), downloadId)
    }

    fun cancelDownload(downloadId: String) {
        DownloadService.cancelDownload(getApplication(), downloadId)
    }

    fun deleteDownload(download: Download) {
        viewModelScope.launch {
            if (download.status == DownloadStatus.DOWNLOADING) {
                cancelDownload(download.id)
            }
            File(download.filePath).delete()
            downloadRepository.deleteDownload(download)
        }
    }

    fun clearCompletedDownloads() {
        viewModelScope.launch {
            downloadRepository.deleteByStatus(DownloadStatus.COMPLETED)
        }
    }

    fun setDownloadFolder(path: String) {
        viewModelScope.launch {
            preferencesManager.setDownloadFolder(path)
        }
    }

    private fun getDefaultDownloadFolder(): String {
        val context = getApplication<Application>()
        return context.getExternalFilesDir(null)?.absolutePath + "/downloads"
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9.-]"), "_")
    }
}
