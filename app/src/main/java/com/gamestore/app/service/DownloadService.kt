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
        startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundNotification())
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
        
        val job = serviceScope.launch {
            try {
                val download = downloadRepository.getDownloadById(downloadId) ?: return@launch
                
                if (download.status == DownloadStatus.COMPLETED) return@launch
                
                pausedDownloads.remove(downloadId)
                downloadRepository.updateStatus(downloadId, DownloadStatus.DOWNLOADING)
                
                performDownload(download)
                
            } catch (e: Exception) {
                e.printStackTrace()
                downloadRepository.updateStatus(downloadId, DownloadStatus.FAILED)
                updateNotification(downloadId, "Download falhou", 0, 0, failed = true)
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
        var connection: HttpURLConnection? = null
        var input: InputStream? = null
        var output: FileOutputStream? = null

        try {
            val file = File(download.filePath)
            file.parentFile?.mkdirs()

            val url = URL(download.url)
            connection = url.openConnection() as HttpURLConnection
            
            val existingBytes = if (file.exists()) file.length() else 0L
            if (existingBytes > 0) {
                connection.setRequestProperty("Range", "bytes=$existingBytes-")
            }
            
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw Exception("Server returned HTTP $responseCode")
            }

            val contentLength = connection.contentLength
            val totalBytes = if (contentLength > 0) {
                contentLength + existingBytes
            } else {
                download.totalBytes
            }

            if (download.totalBytes == 0L) {
                downloadRepository.updateDownload(download.copy(totalBytes = totalBytes))
            }

            input = connection.inputStream
            output = FileOutputStream(file, existingBytes > 0)

            val buffer = ByteArray(8192)
            var bytesRead: Int
            var downloadedBytes = existingBytes
            var lastUpdateTime = System.currentTimeMillis()
            var lastDownloadedBytes = downloadedBytes
            var downloadSpeed = 0L

            while (coroutineContext.isActive && !pausedDownloads.contains(download.id)) {
                bytesRead = input.read(buffer)
                if (bytesRead == -1) break

                output.write(buffer, 0, bytesRead)
                downloadedBytes += bytesRead

                val currentTime = System.currentTimeMillis()
                if (currentTime - lastUpdateTime >= 500) {
                    val timeDiff = currentTime - lastUpdateTime
                    val bytesDiff = downloadedBytes - lastDownloadedBytes
                    downloadSpeed = (bytesDiff * 1000 / timeDiff)
                    
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
            }

        } catch (e: Exception) {
            if (e is CancellationException) throw e
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

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
