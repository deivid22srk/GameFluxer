package com.gamestore.app.util

import android.content.Context
import android.util.Log
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object PythonGoFileExtractor {

    private const val TAG = "PythonGoFileExtractor"
    private var pythonInitialized = false

    fun initPython(context: Context) {
        if (!pythonInitialized) {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(context))
            }
            pythonInitialized = true
            Log.d(TAG, "Python initialized successfully")
        }
    }

    suspend fun extractGoFileLink(
        context: Context,
        url: String,
        password: String? = null
    ): GoFileDownloadInfo? = withContext(Dispatchers.IO) {
        try {
            initPython(context)
            
            Log.d(TAG, "Extracting GoFile link: $url")
            
            val py = Python.getInstance()
            val module = py.getModule("gofile_extractor")
            
            val result = module.callAttr("extract_with_retry", url, password)
            val jsonString = result?.toString()
            
            if (jsonString.isNullOrEmpty() || jsonString == "None") {
                Log.e(TAG, "Python returned null or empty result")
                return@withContext null
            }
            
            Log.d(TAG, "Python result: ${jsonString.take(100)}...")
            
            val jsonObject = JSONObject(jsonString)
            val downloadUrl = jsonObject.optString("url")
            val headersJson = jsonObject.optJSONObject("headers")
            
            if (downloadUrl.isEmpty() || headersJson == null) {
                Log.e(TAG, "Invalid JSON structure")
                return@withContext null
            }
            
            val headers = mutableMapOf<String, String>()
            headersJson.keys().forEach { key ->
                headers[key] = headersJson.getString(key)
            }
            
            Log.d(TAG, "Successfully extracted: $downloadUrl with ${headers.size} headers")
            
            return@withContext GoFileDownloadInfo(
                url = downloadUrl,
                headers = headers
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting GoFile link", e)
            return@withContext null
        }
    }
    
    fun isGoFileUrl(url: String): Boolean {
        return url.contains("gofile.io", ignoreCase = true)
    }
}
