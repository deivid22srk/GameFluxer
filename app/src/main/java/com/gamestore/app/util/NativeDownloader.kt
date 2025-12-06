package com.gamestore.app.util

import android.util.Log
import java.io.File

class NativeDownloader {
    
    companion object {
        private const val TAG = "NativeDownloader"
        
        init {
            try {
                System.loadLibrary("gamefluxer")
                Log.d(TAG, "Native download library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native download library", e)
            }
        }
    }
    
    interface DownloadCallback {
        fun onProgress(bytesDownloaded: Long, totalBytes: Long, speed: Long)
        fun onComplete()
        fun onError(error: String)
    }
    
    data class DownloadProgressInfo(
        val downloadId: Int,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val speed: Long,
        val progress: Int,
        val state: Int,
        val error: String
    )
    
    private external fun startDownloadNative(
        url: String,
        outputPath: String,
        existingBytes: Long,
        headers: Array<String>?,
        callback: DownloadCallback
    ): Int
    
    private external fun pauseDownloadNative(downloadId: Int)
    private external fun resumeDownloadNative(downloadId: Int)
    private external fun cancelDownloadNative(downloadId: Int)
    private external fun getProgressJsonNative(downloadId: Int): String
    
    fun startDownload(
        url: String,
        outputPath: String,
        customHeaders: String? = null,
        callback: DownloadCallback
    ): Int {
        val file = File(outputPath)
        val existingBytes = if (file.exists()) file.length() else 0L
        
        val headersArray = customHeaders?.let { headerString ->
            headerString.split("|")
                .filter { it.contains(":") }
                .toTypedArray()
        }
        
        Log.d(TAG, "Starting native download: $url")
        Log.d(TAG, "Output path: $outputPath")
        Log.d(TAG, "Existing bytes: $existingBytes")
        Log.d(TAG, "Custom headers: ${headersArray?.joinToString()}")
        
        return try {
            startDownloadNative(url, outputPath, existingBytes, headersArray, callback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start native download", e)
            callback.onError("Failed to start download: ${e.message}")
            -1
        }
    }
    
    fun pauseDownload(downloadId: Int) {
        Log.d(TAG, "Pausing download ID: $downloadId")
        try {
            pauseDownloadNative(downloadId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pause download", e)
        }
    }
    
    fun resumeDownload(downloadId: Int) {
        Log.d(TAG, "Resuming download ID: $downloadId")
        try {
            resumeDownloadNative(downloadId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resume download", e)
        }
    }
    
    fun cancelDownload(downloadId: Int) {
        Log.d(TAG, "Cancelling download ID: $downloadId")
        try {
            cancelDownloadNative(downloadId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel download", e)
        }
    }
    
    fun getProgress(downloadId: Int): DownloadProgressInfo? {
        return try {
            val json = getProgressJsonNative(downloadId)
            parseProgressJson(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get progress", e)
            null
        }
    }
    
    private fun parseProgressJson(json: String): DownloadProgressInfo? {
        return try {
            val regex = """"(\w+)":(-?\d+|"[^"]*")""".toRegex()
            val values = mutableMapOf<String, String>()
            
            regex.findAll(json).forEach { match ->
                val key = match.groupValues[1]
                val value = match.groupValues[2].trim('"')
                values[key] = value
            }
            
            DownloadProgressInfo(
                downloadId = values["downloadId"]?.toIntOrNull() ?: 0,
                bytesDownloaded = values["bytesDownloaded"]?.toLongOrNull() ?: 0L,
                totalBytes = values["totalBytes"]?.toLongOrNull() ?: 0L,
                speed = values["speed"]?.toLongOrNull() ?: 0L,
                progress = values["progress"]?.toIntOrNull() ?: 0,
                state = values["state"]?.toIntOrNull() ?: 0,
                error = values["error"] ?: ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse progress JSON", e)
            null
        }
    }
}
