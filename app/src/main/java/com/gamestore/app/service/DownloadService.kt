package com.gamestore.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.gamestore.app.MainActivity
import com.gamestore.app.R
import com.gamestore.app.data.local.GameDatabase
import com.gamestore.app.data.model.Download
import com.gamestore.app.data.model.DownloadStatus
import com.gamestore.app.data.repository.DownloadRepository
import com.gamestore.app.data.preferences.PreferencesManager
import com.gamestore.app.util.DownloadDebugHelper
import com.gamestore.app.util.ShizukuInstaller
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.coroutines.coroutineContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

class DownloadService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val notificationManager by lazy { 
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager 
    }
    private val downloadRepository by lazy {
        DownloadRepository(GameDatabase.getDatabase(applicationContext).downloadDao())
    }
    
    private val preferencesManager by lazy {
        PreferencesManager(applicationContext)
    }
    
    private val activeDownloads = mutableMapOf<String, Job>()
    private val pausedDownloads = mutableSetOf<String>()

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "download_channel"
        private const val NOTIFICATION_CHANNEL_NAME = "Downloads"
        private const val FOREGROUND_NOTIFICATION_ID = 1
        
        const val ACTION_START_DOWNLOAD = "ACTION_START_DOWNLOAD"
        const val ACTION_PAUSE_DOWNLOAD = "ACTION_PAUSE_DOWNLOAD"
        const val ACTION_RESUME_DOWNLOAD = "ACTION_RESUME_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "ACTION_CANCEL_DOWNLOAD"
        const val EXTRA_DOWNLOAD_ID = "EXTRA_DOWNLOAD_ID"

        fun startDownload(context: Context, downloadId: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pauseDownload(context: Context, downloadId: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_PAUSE_DOWNLOAD
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            context.startService(intent)
        }

        fun resumeDownload(context: Context, downloadId: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_RESUME_DOWNLOAD
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancelDownload(context: Context, downloadId: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_CANCEL_DOWNLOAD
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        try {
            startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundNotification())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val downloadId = it.getStringExtra(EXTRA_DOWNLOAD_ID) ?: return@let
            
            when (it.action) {
                ACTION_START_DOWNLOAD -> startDownload(downloadId)
                ACTION_PAUSE_DOWNLOAD -> pauseDownload(downloadId)
                ACTION_RESUME_DOWNLOAD -> resumeDownload(downloadId)
                ACTION_CANCEL_DOWNLOAD -> cancelDownload(downloadId)
            }
        }
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startDownload(downloadId: String) {
        if (activeDownloads.containsKey(downloadId)) return
        
        serviceScope.launch {
            downloadRepository.updateStatus(downloadId, DownloadStatus.DOWNLOADING)
        }
        
        val job = serviceScope.launch {
            try {
                val download = downloadRepository.getDownloadById(downloadId)
                if (download == null) {
                    return@launch
                }
                
                if (download.status == DownloadStatus.COMPLETED) return@launch
                
                pausedDownloads.remove(downloadId)
                
                performDownload(download)
                
            } catch (e: Exception) {
                e.printStackTrace()
                try {
                    downloadRepository.updateStatus(downloadId, DownloadStatus.FAILED)
                    updateNotification(downloadId, "Download falhou", 0, 0, failed = true)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            } finally {
                activeDownloads.remove(downloadId)
                checkIfServiceShouldStop()
            }
        }
        
        activeDownloads[downloadId] = job
    }

    private fun pauseDownload(downloadId: String) {
        pausedDownloads.add(downloadId)
        activeDownloads[downloadId]?.cancel()
        activeDownloads.remove(downloadId)
        
        serviceScope.launch {
            downloadRepository.updateStatus(downloadId, DownloadStatus.PAUSED)
            val download = downloadRepository.getDownloadById(downloadId)
            download?.let {
                updateNotification(
                    downloadId,
                    it.gameName,
                    it.downloadedBytes,
                    it.totalBytes,
                    paused = true
                )
            }
        }
    }

    private fun resumeDownload(downloadId: String) {
        pausedDownloads.remove(downloadId)
        startDownload(downloadId)
    }

    private fun cancelDownload(downloadId: String) {
        activeDownloads[downloadId]?.cancel()
        activeDownloads.remove(downloadId)
        pausedDownloads.remove(downloadId)
        
        serviceScope.launch {
            val download = downloadRepository.getDownloadById(downloadId)
            download?.let {
                File(it.filePath).delete()
                downloadRepository.updateStatus(downloadId, DownloadStatus.CANCELLED)
                notificationManager.cancel(downloadId.hashCode())
            }
            checkIfServiceShouldStop()
        }
    }

    private suspend fun performDownload(download: Download) {
        val downloadUrls = download.url.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        var lastException: Exception? = null
        
        for ((index, downloadUrl) in downloadUrls.withIndex()) {
            try {
                performSingleDownload(download, downloadUrl, index == downloadUrls.size - 1)
                return
            } catch (e: Exception) {
                lastException = e
                if (index < downloadUrls.size - 1) {
                    DownloadDebugHelper.logError("Download failed for URL ${index + 1}, trying next URL", e)
                    continue
                }
            }
        }
        
        throw lastException ?: Exception("No download URLs available")
    }

    private suspend fun performSingleDownload(download: Download, downloadUrl: String, isLastAttempt: Boolean) {
        var connection: HttpURLConnection? = null
        var input: InputStream? = null
        var output: FileOutputStream? = null

        try {
            val file = File(download.filePath)
            file.parentFile?.mkdirs()
            
            val existingBytes = if (file.exists()) file.length() else 0L

            DownloadDebugHelper.logDownloadStart(downloadUrl, download.customHeaders != null)

            val url = URL(downloadUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            
            // Adiciona headers customizados se existirem (necessário para GoFile)
            if (download.customHeaders != null) {
                val headers = parseCustomHeaders(download.customHeaders)
                DownloadDebugHelper.logCustomHeaders(headers)
                headers.forEach { (key, value) ->
                    connection.setRequestProperty(key, value)
                }
            } else {
                // Headers padrão se não houver customizados
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36")
            }
            
            if (downloadUrl.contains("archive.org", ignoreCase = true)) {
                val cookies = getInternetArchiveCookies()
                if (cookies != null) {
                    connection.setRequestProperty("Cookie", cookies)
                }
            }
            
            if (existingBytes > 0) {
                connection.setRequestProperty("Range", "bytes=$existingBytes-")
            }
            
            connection.connect()

            val responseCode = connection.responseCode
            val contentType = connection.contentType
            
            // Debug log
            DownloadDebugHelper.logResponseInfo(responseCode, contentType, connection.contentLengthLong)
            
            // Verifica se está baixando HTML ao invés do arquivo
            if (contentType?.contains("text/html", ignoreCase = true) == true) {
                throw Exception("Server returned HTML instead of file. Response code: $responseCode")
            }
            
            if (responseCode !in 200..299) {
                throw Exception("Server returned HTTP $responseCode")
            }

            // Melhora a extração do tamanho do arquivo
            val totalBytes = extractTotalFileSize(connection, existingBytes)

            if (download.totalBytes == 0L || download.totalBytes != totalBytes) {
                downloadRepository.updateDownload(download.copy(
                    totalBytes = totalBytes,
                    downloadedBytes = existingBytes
                ))
            }
            
            updateNotification(
                download.id,
                download.gameName,
                existingBytes,
                totalBytes,
                downloadSpeed = 0
            )

            input = connection.inputStream
            output = FileOutputStream(file, existingBytes > 0)

            val buffer = ByteArray(8192)
            var bytesRead: Int
            var downloadedBytes = existingBytes
            var lastUpdateTime = System.currentTimeMillis()
            var lastDownloadedBytes = downloadedBytes
            var downloadSpeed = 0L
            var isFirstUpdate = true

            while (coroutineContext.isActive && !pausedDownloads.contains(download.id)) {
                bytesRead = input.read(buffer)
                if (bytesRead == -1) break

                output.write(buffer, 0, bytesRead)
                downloadedBytes += bytesRead

                val currentTime = System.currentTimeMillis()
                val shouldUpdate = (currentTime - lastUpdateTime >= 500) || isFirstUpdate
                
                if (shouldUpdate) {
                    val timeDiff = if (isFirstUpdate) 1 else (currentTime - lastUpdateTime)
                    val bytesDiff = downloadedBytes - lastDownloadedBytes
                    downloadSpeed = if (timeDiff > 0) (bytesDiff * 1000 / timeDiff) else 0
                    
                    downloadRepository.updateProgress(download.id, downloadedBytes)
                    updateNotification(
                        download.id,
                        download.gameName,
                        downloadedBytes,
                        totalBytes,
                        downloadSpeed = downloadSpeed
                    )
                    
                    lastUpdateTime = currentTime
                    lastDownloadedBytes = downloadedBytes
                    isFirstUpdate = false
                }
            }

            if (pausedDownloads.contains(download.id)) {
                return
            }

            if (downloadedBytes >= totalBytes && totalBytes > 0) {
                downloadRepository.updateDownload(
                    download.copy(
                        downloadedBytes = downloadedBytes,
                        totalBytes = totalBytes,
                        status = DownloadStatus.COMPLETED
                    )
                )
                updateNotification(
                    download.id,
                    download.gameName,
                    downloadedBytes,
                    totalBytes,
                    completed = true
                )
                
                if (download.filePath.endsWith(".apk", ignoreCase = true)) {
                    serviceScope.launch {
                        tryAutoInstall(download, download.filePath)
                    }
                }
            }

        } catch (e: Exception) {
            if (e is CancellationException) throw e
            DownloadDebugHelper.logError("Download failed for ${download.gameName}", e)
            e.printStackTrace()
            throw e
        } finally {
            output?.close()
            input?.close()
            connection?.disconnect()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundNotification() = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
        .setContentTitle("GameFluxer")
        .setContentText("Serviço de download ativo")
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .build()

    private fun updateNotification(
        downloadId: String,
        gameName: String,
        downloadedBytes: Long,
        totalBytes: Long,
        downloadSpeed: Long = 0,
        completed: Boolean = false,
        failed: Boolean = false,
        paused: Boolean = false
    ) {
        try {
            val notificationId = downloadId.hashCode()
            
            val intent = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this,
                notificationId,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(gameName)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)

            when {
                completed -> {
                    builder.setContentText("Download concluído")
                        .setProgress(0, 0, false)
                        .setOngoing(false)
                }
                failed -> {
                    builder.setContentText("Download falhou")
                        .setProgress(0, 0, false)
                        .setOngoing(false)
                        .setSmallIcon(android.R.drawable.stat_notify_error)
                }
                paused -> {
                    val progress = if (totalBytes > 0) {
                        ((downloadedBytes.toFloat() / totalBytes.toFloat()) * 100).roundToInt()
                    } else 0
                    
                    builder.setContentText("Pausado - ${formatBytes(downloadedBytes)} / ${formatBytes(totalBytes)} ($progress%)")
                        .setProgress(100, progress, false)
                        .setOngoing(false)
                        .addAction(
                            android.R.drawable.ic_media_play,
                            "Retomar",
                            createActionPendingIntent(downloadId, ACTION_RESUME_DOWNLOAD)
                        )
                        .addAction(
                            android.R.drawable.ic_delete,
                            "Cancelar",
                            createActionPendingIntent(downloadId, ACTION_CANCEL_DOWNLOAD)
                        )
                }
                else -> {
                    val progress = if (totalBytes > 0) {
                        ((downloadedBytes.toFloat() / totalBytes.toFloat()) * 100).roundToInt()
                    } else 0
                    
                    val speedText = if (downloadSpeed > 0) " - ${formatBytes(downloadSpeed)}/s" else ""
                    
                    builder.setContentText("${formatBytes(downloadedBytes)} / ${formatBytes(totalBytes)} ($progress%)$speedText")
                        .setProgress(100, progress, totalBytes == 0L)
                        .setOngoing(true)
                        .addAction(
                            android.R.drawable.ic_media_pause,
                            "Pausar",
                            createActionPendingIntent(downloadId, ACTION_PAUSE_DOWNLOAD)
                        )
                        .addAction(
                            android.R.drawable.ic_delete,
                            "Cancelar",
                            createActionPendingIntent(downloadId, ACTION_CANCEL_DOWNLOAD)
                        )
                }
            }

            notificationManager.notify(notificationId, builder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createActionPendingIntent(downloadId: String, action: String): PendingIntent {
        val intent = Intent(this, DownloadService::class.java).apply {
            this.action = action
            putExtra(EXTRA_DOWNLOAD_ID, downloadId)
        }
        return PendingIntent.getService(
            this,
            "$downloadId-$action".hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.2f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    private fun parseCustomHeaders(headersString: String): Map<String, String> {
        return try {
            headersString.split("|")
                .mapNotNull { header ->
                    val parts = header.split(":", limit = 2)
                    if (parts.size == 2) {
                        parts[0] to parts[1]
                    } else {
                        null
                    }
                }
                .toMap()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyMap()
        }
    }

    private fun extractTotalFileSize(connection: HttpURLConnection, existingBytes: Long): Long {
        return try {
            // Se for resume (existingBytes > 0), verifica Content-Range primeiro
            if (existingBytes > 0) {
                val contentRange = connection.getHeaderField("Content-Range")
                if (contentRange != null) {
                    // Content-Range: bytes 1000-2000/3000
                    val parts = contentRange.split("/")
                    if (parts.size == 2) {
                        val total = parts[1].toLongOrNull()
                        if (total != null && total > 0) {
                            return total
                        }
                    }
                }
            }
            
            // Tenta obter do Content-Length
            val contentLengthHeader = connection.getHeaderField("Content-Length")
            val contentLength = contentLengthHeader?.toLongOrNull() ?: connection.contentLengthLong
            
            if (contentLength > 0) {
                return contentLength + existingBytes
            }
            
            // Se não conseguir, retorna 0 (download com progresso indeterminado)
            return 0L
        } catch (e: Exception) {
            e.printStackTrace()
            return 0L
        }
    }

    private fun checkIfServiceShouldStop() {
        serviceScope.launch {
            val activeDownloads = downloadRepository.getDownloadsByStatus(
                listOf(DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED)
            ).first()
            
            if (activeDownloads.isEmpty() && this@DownloadService.activeDownloads.isEmpty()) {
                stopForeground(true)
                stopSelf()
            }
        }
    }

    private suspend fun getInternetArchiveCookies(): String? {
        return try {
            val cookies = preferencesManager.internetArchiveCookies.first()
            
            if (cookies.isEmpty()) {
                return null
            }
            
            cookies
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private suspend fun tryAutoInstall(download: Download, filePath: String) {
        try {
            val installMethod = preferencesManager.installMethod.first()
            
            if (installMethod != "shizuku") {
                return
            }
            
            val shizukuInstaller = ShizukuInstaller(applicationContext)
            
            if (!shizukuInstaller.isShizukuAvailable() || !shizukuInstaller.hasShizukuPermission()) {
                showInstallNotification(
                    download.id,
                    download.gameName,
                    "Shizuku não disponível",
                    false
                )
                return
            }
            
            val apkFile = File(filePath)
            if (!apkFile.exists()) {
                showInstallNotification(
                    download.id,
                    download.gameName,
                    "Arquivo APK não encontrado",
                    false
                )
                return
            }
            
            showInstallNotification(
                download.id,
                download.gameName,
                "Iniciando instalação...",
                null
            )
            
            shizukuInstaller.installApk(
                apkFile,
                onProgress = { progress ->
                    showInstallNotification(
                        download.id,
                        download.gameName,
                        progress,
                        null
                    )
                },
                onComplete = { success, error ->
                    if (success) {
                        showInstallNotification(
                            download.id,
                            download.gameName,
                            "Instalação concluída com sucesso",
                            true
                        )
                    } else {
                        showInstallNotification(
                            download.id,
                            download.gameName,
                            "Erro: ${error ?: "Desconhecido"}",
                            false
                        )
                    }
                }
            )
            
        } catch (e: Exception) {
            e.printStackTrace()
            showInstallNotification(
                download.id,
                download.gameName,
                "Erro ao instalar: ${e.message}",
                false
            )
        }
    }
    
    private fun showInstallNotification(
        downloadId: String,
        gameName: String,
        message: String,
        success: Boolean?
    ) {
        val notificationId = "install_$downloadId".hashCode()
        
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val icon = when (success) {
            true -> android.R.drawable.stat_sys_download_done
            false -> android.R.drawable.stat_notify_error
            null -> android.R.drawable.stat_sys_download
        }
        
        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle("$gameName - Instalação")
            .setContentText(message)
            .setContentIntent(pendingIntent)
            .setAutoCancel(success != null)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        
        if (success == null) {
            builder.setProgress(0, 0, true)
        }
        
        notificationManager.notify(notificationId, builder.build())
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
