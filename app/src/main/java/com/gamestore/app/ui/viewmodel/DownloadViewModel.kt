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
import com.gamestore.app.util.MediaFireExtractor
import com.gamestore.app.util.GoFileExtractor
import com.gamestore.app.util.PythonGoFileExtractor
import com.gamestore.app.util.GoogleDriveExtractor
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

    fun getDownloadForGame(gameId: String): Flow<Download?> = 
        downloadRepository.observeDownloadByGameId(gameId)

    fun startDownload(game: Game) {
        startDownloadInternal(game, null)
    }
    
    fun startDownloadWithCustomUrl(game: Game, customUrl: String) {
        startDownloadInternal(game, customUrl)
    }
    
    private fun startDownloadInternal(game: Game, customUrl: String?) {
        viewModelScope.launch {
            try {
                val existingDownload = downloadRepository.getDownloadByGameId(game.id)
                
                if (existingDownload != null) {
                    if (existingDownload.status == DownloadStatus.PAUSED ||
                        existingDownload.status == DownloadStatus.FAILED) {
                        DownloadService.resumeDownload(getApplication(), existingDownload.id)
                    }
                    return@launch
                }

                val folder = downloadFolder.value ?: getDefaultDownloadFolder()
                val folderFile = File(folder)
                if (!folderFile.exists()) {
                    folderFile.mkdirs()
                }
                
                var downloadUrl = customUrl ?: game.downloadUrl
                var customHeaders: String? = null
                
                when {
                    PythonGoFileExtractor.isGoFileUrl(downloadUrl) -> {
                        val goFileInfo = PythonGoFileExtractor.extractGoFileLink(
                            getApplication(),
                            downloadUrl
                        )
                        if (goFileInfo != null) {
                            downloadUrl = goFileInfo.url
                            customHeaders = goFileInfo.headers.entries.joinToString("|") { "${it.key}:${it.value}" }
                        }
                    }
                    MediaFireExtractor.isMediaFireUrl(downloadUrl) -> {
                        downloadUrl = MediaFireExtractor.extractDirectDownloadLinkWithRetry(downloadUrl)
                    }
                    GoogleDriveExtractor.isGoogleDriveUrl(downloadUrl) -> {
                        downloadUrl = GoogleDriveExtractor.extractDirectDownloadLinkWithRetry(downloadUrl)
                    }
                }
                
                val fileExtension = detectFileExtension(downloadUrl, game.name)
                val fileName = sanitizeFileName(game.name) + "_" + game.version + fileExtension
                val filePath = File(folder, fileName).absolutePath

                val download = Download(
                    id = UUID.randomUUID().toString(),
                    gameId = game.id,
                    gameName = game.name,
                    gameIconUrl = game.iconUrl,
                    url = downloadUrl,
                    filePath = filePath,
                    status = DownloadStatus.QUEUED,
                    customHeaders = customHeaders
                )

                downloadRepository.insertDownload(download)
                DownloadService.startDownload(getApplication(), download.id)
            } catch (e: Exception) {
                e.printStackTrace()
            }
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

    private fun detectFileExtension(url: String, gameName: String): String {
        val cleanUrl = url.substringBefore("?").lowercase()
        
        val commonExtensions = listOf(
            ".iso", ".zip", ".7z", ".rar", ".apk", ".xapk",
            ".exe", ".msi", ".bin", ".img", ".cso", ".daa",
            ".tar", ".gz", ".bz2", ".torrent"
        )
        
        for (ext in commonExtensions) {
            if (cleanUrl.endsWith(ext)) {
                return ext
            }
        }
        
        if (gameName.lowercase().contains("ps2") || gameName.lowercase().contains("playstation")) {
            return ".iso"
        }
        
        if (gameName.lowercase().contains("android")) {
            return ".apk"
        }
        
        return ".zip"
    }

    private fun getDefaultDownloadFolder(): String {
        return "/storage/emulated/0/GameFluxer"
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9.-]"), "_")
    }
}
