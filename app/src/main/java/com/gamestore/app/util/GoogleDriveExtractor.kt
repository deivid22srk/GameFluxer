package com.gamestore.app.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

data class GoogleDriveDownloadInfo(
    val url: String,
    val needsConfirmation: Boolean = false
)

object GoogleDriveExtractor {

    private const val TAG = "GoogleDriveExtractor"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
    private const val BASE_URL = "https://drive.usercontent.google.com/download?id={id}&export=download&authuser=0"
    private const val CHUNK_SIZE = 64 * 1024
    
    // Padrões para extrair o ID do Google Drive de várias formas de URL
    private val ID_PATTERNS = listOf(
        Pattern.compile("/file/d/([0-9A-Za-z_-]{10,})(?:/|\$)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("/folders/([0-9A-Za-z_-]{10,})(?:/|\$)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("id=([0-9A-Za-z_-]{10,})(?:&|\$)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("([0-9A-Za-z_-]{10,})", Pattern.CASE_INSENSITIVE)
    )
    
    // Padrões para extrair token de confirmação
    private val CONFIRM_PATTERNS = listOf(
        Pattern.compile("confirm=([0-9A-Za-z_-]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("name=\"confirm\"\\s+value=\"([0-9A-Za-z_-]+)\"", Pattern.CASE_INSENSITIVE)
    )
    
    // Padrão para extrair UUID
    private val UUID_PATTERN = Pattern.compile("name=\"uuid\"\\s+value=\"([0-9A-Za-z_-]+)\"", Pattern.CASE_INSENSITIVE)
    
    /**
     * Verifica se a URL é do Google Drive
     */
    fun isGoogleDriveUrl(url: String): Boolean {
        return url.contains("drive.google.com", ignoreCase = true) ||
               url.contains("drive.usercontent.google.com", ignoreCase = true)
    }
    
    /**
     * Extrai o ID do arquivo do Google Drive da URL
     */
    private fun extractFileId(url: String): String? {
        for (pattern in ID_PATTERNS) {
            val matcher = pattern.matcher(url)
            if (matcher.find()) {
                return matcher.group(1)
            }
        }
        return null
    }
    
    /**
     * Extrai o link de download direto do Google Drive
     * @param url URL do Google Drive (várias formas aceitas)
     * @return URL de download direto ou null se falhar
     */
    suspend fun extractDirectDownloadLink(url: String): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Extracting download link from: $url")
            
            // Extrai o ID do arquivo
            val fileId = extractFileId(url)
            if (fileId == null) {
                Log.e(TAG, "Failed to extract file ID from URL")
                return@withContext null
            }
            Log.d(TAG, "File ID: $fileId")
            
            // Tenta obter o link direto (pode precisar de confirmação)
            val directLink = getDirectLink(fileId)
            if (directLink != null) {
                Log.d(TAG, "Successfully extracted direct link: $directLink")
                return@withContext directLink
            }
            
            Log.e(TAG, "Failed to extract direct link")
            return@withContext null
            
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting download link", e)
            return@withContext null
        }
    }
    
    /**
     * Obtém o link direto, tratando confirmação se necessário
     */
    private suspend fun getDirectLink(
        fileId: String, 
        confirm: String? = null, 
        uuid: String? = null,
        attempt: Int = 0
    ): String? = withContext(Dispatchers.IO) {
        if (attempt > 2) {
            Log.e(TAG, "Max attempts reached")
            return@withContext null
        }
        
        var connection: HttpURLConnection? = null
        
        try {
            // Constrói a URL
            var downloadUrl = BASE_URL.replace("{id}", fileId)
            if (confirm != null) {
                downloadUrl += "&confirm=$confirm"
            }
            if (uuid != null) {
                downloadUrl += "&uuid=$uuid"
            }
            
            Log.d(TAG, "Attempting download URL: $downloadUrl")
            
            val url = URL(downloadUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.instanceFollowRedirects = true
            connection.connect()
            
            val responseCode = connection.responseCode
            Log.d(TAG, "Response code: $responseCode")
            
            if (responseCode !in 200..299) {
                Log.e(TAG, "Invalid response code: $responseCode")
                return@withContext null
            }
            
            // Verifica se precisa de autenticação
            val finalUrl = connection.url.toString()
            if (finalUrl.contains("ServiceLogin", ignoreCase = true)) {
                Log.e(TAG, "File does not have link sharing enabled")
                return@withContext null
            }
            
            // Verifica se tem content-disposition (indica download direto)
            val contentDisposition = connection.getHeaderField("Content-Disposition")
            if (contentDisposition != null) {
                Log.d(TAG, "Content-Disposition found, download is ready")
                // URL atual é o link direto
                return@withContext downloadUrl
            }
            
            Log.d(TAG, "No Content-Disposition, checking for confirmation token")
            
            // Lê parte do HTML para buscar token de confirmação
            val htmlChunk = BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                val buffer = CharArray(CHUNK_SIZE)
                val length = reader.read(buffer)
                if (length > 0) String(buffer, 0, length) else ""
            }
            
            Log.d(TAG, "HTML chunk size: ${htmlChunk.length}")
            
            // Verifica quota exceeded
            if (htmlChunk.contains("Google Drive - Quota exceeded", ignoreCase = true) ||
                htmlChunk.contains("Too many users have viewed or downloaded this file recently", ignoreCase = true)) {
                Log.e(TAG, "Quota exceeded for this file")
                return@withContext null
            }
            
            // Procura pelo token de confirmação
            var confirmToken: String? = null
            for (pattern in CONFIRM_PATTERNS) {
                val matcher = pattern.matcher(htmlChunk)
                if (matcher.find()) {
                    confirmToken = matcher.group(1)
                    Log.d(TAG, "Found confirm token: $confirmToken")
                    break
                }
            }
            
            // Procura pelo UUID
            var uuidToken: String? = null
            val uuidMatcher = UUID_PATTERN.matcher(htmlChunk)
            if (uuidMatcher.find()) {
                uuidToken = uuidMatcher.group(1)
                Log.d(TAG, "Found UUID: $uuidToken")
            }
            
            // Se encontrou token, tenta novamente com confirmação
            if (confirmToken != null) {
                return@withContext getDirectLink(fileId, confirmToken, uuidToken, attempt + 1)
            }
            
            // Tenta com 't' como último recurso (padrão do Python)
            if (attempt == 0) {
                Log.d(TAG, "Trying confirmation 't' as last resort")
                return@withContext getDirectLink(fileId, "t", uuidToken, attempt + 1)
            }
            
            Log.e(TAG, "Could not find confirmation token")
            return@withContext null
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting direct link", e)
            return@withContext null
        } finally {
            connection?.disconnect()
        }
    }
    
    /**
     * Extrai o link de download direto com retry
     * @param url URL do Google Drive
     * @param maxRetries Número máximo de tentativas
     * @return URL de download direto ou a URL original se falhar
     */
    suspend fun extractDirectDownloadLinkWithRetry(
        url: String,
        maxRetries: Int = 3
    ): String {
        repeat(maxRetries) { attempt ->
            try {
                val directLink = extractDirectDownloadLink(url)
                if (directLink != null && directLink.isNotEmpty()) {
                    return directLink
                }
            } catch (e: Exception) {
                Log.e(TAG, "Attempt ${attempt + 1} failed", e)
                if (attempt == maxRetries - 1) {
                    return url
                }
            }
        }
        
        // Retorna URL original se falhar
        return url
    }
}
