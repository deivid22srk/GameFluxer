package com.gamestore.app.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class GoFileDownloadInfo(
    val url: String,
    val headers: Map<String, String>
)

object GoFileExtractor {

    private const val TAG = "GoFileExtractor"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36"
    private const val GOFILE_API = "https://api.gofile.io"
    private const val GOFILE_API_ACCOUNTS = "$GOFILE_API/accounts"
    private var cachedToken: String? = null
    
    fun isGoFileUrl(url: String): Boolean {
        return url.contains("gofile.io", ignoreCase = true)
    }
    
    private fun extractContentId(url: String): String? {
        return try {
            val parts = url.trim().trimEnd('/').split("/")
            val dIndex = parts.indexOf("d")
            if (dIndex != -1 && dIndex + 1 < parts.size) {
                parts[dIndex + 1].split("?").firstOrNull()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract content ID", e)
            null
        }
    }
    
    private suspend fun createAccountToken(maxRetries: Int = 5): String? = withContext(Dispatchers.IO) {
        cachedToken?.let { return@withContext it }
        
        repeat(maxRetries) { attempt ->
            var connection: HttpURLConnection? = null
            try {
                Log.d(TAG, "Creating account token (attempt ${attempt + 1}/$maxRetries)")
                
                val url = URL(GOFILE_API_ACCOUNTS)
                connection = url.openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "POST"
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Accept-Encoding", "gzip, deflate, br")
                    setRequestProperty("Accept", "*/*")
                    setRequestProperty("Connection", "keep-alive")
                    connectTimeout = 15000
                    readTimeout = 15000
                    doOutput = false
                }
                
                connection.connect()
                
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    Log.e(TAG, "Account creation failed with code: $responseCode")
                    connection.disconnect()
                    if (attempt < maxRetries - 1) return@repeat
                    return@withContext null
                }
                
                val response = BufferedReader(InputStreamReader(connection.inputStream)).use { 
                    it.readText() 
                }
                
                val jsonResponse = JSONObject(response)
                if (jsonResponse.optString("status") == "ok") {
                    val token = jsonResponse.optJSONObject("data")?.optString("token")
                    if (!token.isNullOrEmpty()) {
                        cachedToken = token
                        Log.d(TAG, "Account token created successfully: ${token.take(10)}...")
                        return@withContext token
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Exception creating account (attempt ${attempt + 1})", e)
            } finally {
                connection?.disconnect()
            }
        }
        
        return@withContext null
    }
    
    private suspend fun getContentInfo(
        contentId: String,
        token: String,
        password: String? = null,
        maxRetries: Int = 5
    ): JSONObject? = withContext(Dispatchers.IO) {
        repeat(maxRetries) { attempt ->
            var connection: HttpURLConnection? = null
            try {
                Log.d(TAG, "Getting content info (attempt ${attempt + 1}/$maxRetries)")
                
                var apiUrl = "$GOFILE_API/contents/$contentId?wt=4fd6sg89d7s6&cache=true"
                if (!password.isNullOrEmpty()) {
                    apiUrl += "&password=$password"
                }
                
                val url = URL(apiUrl)
                connection = url.openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Accept-Encoding", "gzip, deflate, br")
                    setRequestProperty("Accept", "*/*")
                    setRequestProperty("Connection", "keep-alive")
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("Cookie", "accountToken=$token")
                    connectTimeout = 15000
                    readTimeout = 15000
                }
                
                connection.connect()
                
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    Log.e(TAG, "Content info failed with code: $responseCode")
                    connection.disconnect()
                    if (attempt < maxRetries - 1) return@repeat
                    return@withContext null
                }
                
                val response = BufferedReader(InputStreamReader(connection.inputStream)).use {
                    it.readText()
                }
                
                Log.d(TAG, "Content info response received")
                return@withContext JSONObject(response)
                
            } catch (e: Exception) {
                Log.e(TAG, "Exception getting content info (attempt ${attempt + 1})", e)
            } finally {
                connection?.disconnect()
            }
        }
        
        return@withContext null
    }
    
    suspend fun extractDirectDownloadLink(url: String, password: String? = null): GoFileDownloadInfo? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting extraction from: $url")
            
            val contentId = extractContentId(url)
            if (contentId.isNullOrEmpty()) {
                Log.e(TAG, "Failed to extract content ID")
                return@withContext null
            }
            Log.d(TAG, "Content ID: $contentId")
            
            val token = createAccountToken()
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "Failed to get account token")
                return@withContext null
            }
            
            val jsonResponse = getContentInfo(contentId, token, password)
            if (jsonResponse == null) {
                Log.e(TAG, "Failed to get content info")
                return@withContext null
            }
            
            if (jsonResponse.optString("status") != "ok") {
                Log.e(TAG, "API status not ok")
                return@withContext null
            }
            
            val data = jsonResponse.optJSONObject("data")
            if (data == null) {
                Log.e(TAG, "No data in response")
                return@withContext null
            }
            
            if (data.has("password") && data.has("passwordStatus")) {
                val passwordStatus = data.optString("passwordStatus")
                if (passwordStatus != "passwordOk" && !password.isNullOrEmpty()) {
                    Log.e(TAG, "Password required or incorrect")
                    return@withContext null
                }
            }
            
            val headers = mutableMapOf(
                "User-Agent" to USER_AGENT,
                "Accept-Encoding" to "gzip, deflate, br",
                "Accept" to "*/*",
                "Connection" to "keep-alive",
                "Cookie" to "accountToken=$token"
            )
            
            val contentType = data.optString("type")
            Log.d(TAG, "Content type: $contentType")
            
            when (contentType) {
                "file" -> {
                    val directLink = data.optString("link")
                    if (directLink.isNotEmpty()) {
                        Log.d(TAG, "Direct link found: $directLink")
                        val downloadUrl = if (directLink.startsWith("http")) directLink else "https:$directLink"
                        headers["Referer"] = downloadUrl
                        return@withContext GoFileDownloadInfo(downloadUrl, headers)
                    }
                }
                "folder" -> {
                    val children = data.optJSONObject("children")
                    if (children != null && children.length() > 0) {
                        Log.d(TAG, "Folder with ${children.length()} children")
                        
                        val keys = children.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val child = children.optJSONObject(key)
                            if (child != null && child.optString("type") == "file") {
                                val directLink = child.optString("link")
                                if (directLink.isNotEmpty()) {
                                    Log.d(TAG, "File in folder found: $directLink")
                                    val downloadUrl = if (directLink.startsWith("http")) directLink else "https:$directLink"
                                    headers["Referer"] = downloadUrl
                                    return@withContext GoFileDownloadInfo(downloadUrl, headers)
                                }
                            }
                        }
                    }
                }
            }
            
            Log.e(TAG, "No download link found")
            return@withContext null
            
        } catch (e: Exception) {
            Log.e(TAG, "Exception in extraction", e)
            return@withContext null
        }
    }
    
    suspend fun extractDirectDownloadLinkWithRetry(
        url: String,
        password: String? = null,
        maxRetries: Int = 3
    ): GoFileDownloadInfo? {
        repeat(maxRetries) { attempt ->
            try {
                Log.d(TAG, "Extraction attempt ${attempt + 1}/$maxRetries")
                val result = extractDirectDownloadLink(url, password)
                if (result != null) {
                    return result
                }
            } catch (e: Exception) {
                Log.e(TAG, "Retry attempt ${attempt + 1} failed", e)
            }
        }
        
        Log.e(TAG, "All extraction attempts failed")
        return null
    }
}
