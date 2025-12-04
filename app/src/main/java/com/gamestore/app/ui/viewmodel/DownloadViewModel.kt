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
                
                val fileName = sanitizeFileName(game.name) + "_" + game.version + ".apk"
                val filePath = File(folder, fileName).absolutePath

                // Extrai o link direto se for MediaFire, GoFile ou Google Drive
                var downloadUrl = game.downloadUrl
                var customHeaders: String? = null
                
                when {
                    MediaFireExtractor.isMediaFireUrl(game.downloadUrl) -> {
                        downloadUrl = MediaFireExtractor.extractDirectDownloadLinkWithRetry(game.downloadUrl)
                    }
                    GoFileExtractor.isGoFileUrl(game.downloadUrl) -> {
                        val goFileInfo = GoFileExtractor.extractDirectDownloadLinkWithRetry(game.downloadUrl)
                        if (goFileInfo != null) {
                            downloadUrl = goFileInfo.url
                            // Serializa os headers para String (formato: key1:value1|key2:value2)
                            customHeaders = goFileInfo.headers.entries.joinToString("|") { "${it.key}:${it.value}" }
                        }
                    }
                    GoogleDriveExtractor.isGoogleDriveUrl(game.downloadUrl) -> {
                        downloadUrl = GoogleDriveExtractor.extractDirectDownloadLinkWithRetry(game.downloadUrl)
                    }
                }

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

    private fun getDefaultDownloadFolder(): String {
        val context = getApplication<Application>()
        val externalDir = context.getExternalFilesDir(null)
        return if (externalDir != null) {
            externalDir.absolutePath + "/downloads"
        } else {
            context.filesDir.absolutePath + "/downloads"
        }
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9.-]"), "_")
    }
}
