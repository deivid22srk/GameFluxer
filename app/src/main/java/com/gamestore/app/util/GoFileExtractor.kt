package com.gamestore.app.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

object GoFileExtractor {

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.5481.178 Safari/537.36"
    private var cachedToken: String? = null
    
    /**
     * Verifica se a URL é do GoFile
     */
    fun isGoFileUrl(url: String): Boolean {
        return url.contains("gofile.io", ignoreCase = true)
    }
    
    /**
     * Extrai o content ID da URL do GoFile
     * Exemplo: https://gofile.io/d/I7Y2WU -> I7Y2WU
     */
    private fun extractContentId(url: String): String? {
        return try {
            val parts = url.split("/")
            val dIndex = parts.indexOf("d")
            if (dIndex != -1 && dIndex + 1 < parts.size) {
                parts[dIndex + 1].split("?").firstOrNull()
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Obtém ou cria um token de acesso para a API do GoFile
     */
    private suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) {
        if (cachedToken != null) {
            return@withContext cachedToken
        }
        
        var connection: HttpURLConnection? = null
        
        try {
            val url = URL("https://api.gofile.io/accounts")
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "*/*")
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.connect()
            
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                return@withContext null
            }
            
            val content = BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                reader.readText()
            }
            
            val jsonResponse = JSONObject(content)
            if (jsonResponse.optString("status") == "ok") {
                val token = jsonResponse.optJSONObject("data")?.optString("token")
                cachedToken = token
                return@withContext token
            }
            
            return@withContext null
            
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        } finally {
            connection?.disconnect()
        }
    }
    
    /**
     * Extrai o link de download direto do GoFile
     * @param url URL da página do GoFile (ex: https://gofile.io/d/I7Y2WU)
     * @param password Senha opcional se o conteúdo for protegido
     * @return URL de download direto ou null se falhar
     */
    suspend fun extractDirectDownloadLink(url: String, password: String? = null): String? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        
        try {
            val contentId = extractContentId(url)
            if (contentId == null) {
                return@withContext null
            }
            
            val token = getAccessToken()
            if (token == null) {
                return@withContext null
            }
            
            var apiUrl = "https://api.gofile.io/contents/$contentId?wt=4fd6sg89d7s6&cache=true"
            if (password != null) {
                apiUrl += "&password=$password"
            }
            
            val urlObj = URL(apiUrl)
            connection = urlObj.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "*/*")
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.connect()
            
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                return@withContext null
            }
            
            val content = BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                reader.readText()
            }
            
            val jsonResponse = JSONObject(content)
            if (jsonResponse.optString("status") != "ok") {
                return@withContext null
            }
            
            val data = jsonResponse.optJSONObject("data")
            if (data == null) {
                return@withContext null
            }
            
            // Verifica se precisa de senha
            if (data.has("password") && data.has("passwordStatus") && 
                data.optString("passwordStatus") != "passwordOk") {
                return@withContext null
            }
            
            // Se for um arquivo único, retorna o link direto
            if (data.optString("type") == "file" || data.has("link")) {
                val directLink = data.optString("link")
                if (directLink.isNotEmpty()) {
                    return@withContext directLink
                }
            }
            
            // Se for uma pasta, pega o primeiro arquivo
            if (data.optString("type") == "folder") {
                val children = data.optJSONObject("children")
                if (children != null && children.length() > 0) {
                    // Pega o primeiro arquivo da pasta
                    val firstKey = children.keys().next()
                    val firstFile = children.optJSONObject(firstKey)
                    if (firstFile != null) {
                        val directLink = firstFile.optString("link")
                        if (directLink.isNotEmpty()) {
                            return@withContext directLink
                        }
                    }
                }
            }
            
            return@withContext null
            
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        } finally {
            connection?.disconnect()
        }
    }
    
    /**
     * Extrai o link de download direto com retry
     * @param url URL da página do GoFile
     * @param password Senha opcional
     * @param maxRetries Número máximo de tentativas
     * @return URL de download direto ou a URL original se falhar
     */
    suspend fun extractDirectDownloadLinkWithRetry(
        url: String, 
        password: String? = null,
        maxRetries: Int = 3
    ): String {
        repeat(maxRetries) { attempt ->
            try {
                val directLink = extractDirectDownloadLink(url, password)
                if (directLink != null) {
                    return directLink
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (attempt == maxRetries - 1) {
                    return url
                }
            }
        }
        
        return url
    }
}
