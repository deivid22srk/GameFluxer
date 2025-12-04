package com.gamestore.app.util

import android.util.Log

object DownloadDebugHelper {
    private const val TAG = "DownloadDebug"
    
    fun logDownloadStart(url: String, hasCustomHeaders: Boolean) {
        Log.d(TAG, "=== Starting Download ===")
        Log.d(TAG, "URL: $url")
        Log.d(TAG, "Has Custom Headers: $hasCustomHeaders")
    }
    
    fun logCustomHeaders(headers: Map<String, String>) {
        Log.d(TAG, "=== Custom Headers ===")
        headers.forEach { (key, value) ->
            // Não loga valores sensíveis completos
            val displayValue = if (key.contains("Authorization", ignoreCase = true) || 
                                   key.contains("Cookie", ignoreCase = true)) {
                "${value.take(10)}..."
            } else {
                value
            }
            Log.d(TAG, "$key: $displayValue")
        }
    }
    
    fun logResponseInfo(responseCode: Int, contentType: String?, contentLength: Long) {
        Log.d(TAG, "=== Response Info ===")
        Log.d(TAG, "Response Code: $responseCode")
        Log.d(TAG, "Content-Type: $contentType")
        Log.d(TAG, "Content-Length: $contentLength")
    }
    
    fun logError(message: String, error: Throwable? = null) {
        Log.e(TAG, "ERROR: $message", error)
    }
}
