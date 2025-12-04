package com.gamestore.app.util

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
            if (!url.split("/").contains("d")) {
                return null
            }
            
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
     * Segue exatamente a implementação do Python
     */
    private suspend fun getAccessToken(maxRetries: Int = 5): String? = withContext(Dispatchers.IO) {
        // Retorna token em cache se disponível
        if (cachedToken != null) {
            return@withContext cachedToken
        }
        
        var connection: HttpURLConnection? = null
        
        repeat(maxRetries) { attempt ->
            try {
                val url = URL("https://api.gofile.io/accounts")
                connection = url.openConnection() as HttpURLConnection
                connection?.apply {
                    requestMethod = "POST"
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Accept-Encoding", "gzip")
                    setRequestProperty("Connection", "keep-alive")
                    setRequestProperty("Accept", "*/*")
                    connectTimeout = 15000
                    readTimeout = 15000
                    doOutput = false
                }
                
                connection?.connect()
                
                val responseCode = connection?.responseCode ?: 0
                if (responseCode !in 200..299) {
                    if (attempt < maxRetries - 1) {
                        connection?.disconnect()
                        return@repeat
                    }
                    return@withContext null
                }
                
                val content = BufferedReader(InputStreamReader(connection?.inputStream)).use { reader ->
                    reader.readText()
                }
                
                val jsonResponse = JSONObject(content)
                if (jsonResponse.optString("status") == "ok") {
                    val token = jsonResponse.optJSONObject("data")?.optString("token")
                    if (token != null && token.isNotEmpty()) {
                        cachedToken = token
                        return@withContext token
                    }
                }
                
                if (attempt < maxRetries - 1) {
                    connection?.disconnect()
                    return@repeat
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
                if (attempt < maxRetries - 1) {
                    connection?.disconnect()
                    return@repeat
                }
            } finally {
                if (attempt == maxRetries - 1) {
                    connection?.disconnect()
                }
            }
        }
        
        return@withContext null
    }
    
    /**
     * Faz requisição GET para a API do GoFile com todos os headers e cookies corretos
     */
    private suspend fun getContentInfo(
        contentId: String, 
        token: String, 
        password: String? = null,
        maxRetries: Int = 5
    ): JSONObject? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        
        repeat(maxRetries) { attempt ->
            try {
                // URL exatamente como no Python
                var apiUrl = "https://api.gofile.io/contents/$contentId?wt=4fd6sg89d7s6&cache=true&sortField=createTime&sortDirection=1"
                
                if (password != null && password.isNotEmpty()) {
                    apiUrl += "&password=$password"
                }
                
                val url = URL(apiUrl)
                connection = url.openConnection() as HttpURLConnection
                connection?.apply {
                    requestMethod = "GET"
                    // Headers exatamente como no Python
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Accept-Encoding", "gzip")
                    setRequestProperty("Connection", "keep-alive")
                    setRequestProperty("Accept", "*/*")
                    setRequestProperty("Authorization", "Bearer $token")
                    // Cookie com accountToken
                    setRequestProperty("Cookie", "accountToken=$token")
                    connectTimeout = 15000
                    readTimeout = 15000
                }
                
                connection?.connect()
                
                val responseCode = connection?.responseCode ?: 0
                if (responseCode !in 200..299) {
                    if (attempt < maxRetries - 1) {
                        connection?.disconnect()
                        return@repeat
                    }
                    return@withContext null
                }
                
                val content = BufferedReader(InputStreamReader(connection?.inputStream)).use { reader ->
                    reader.readText()
                }
                
                val jsonResponse = JSONObject(content)
                return@withContext jsonResponse
                
            } catch (e: Exception) {
                e.printStackTrace()
                if (attempt < maxRetries - 1) {
                    connection?.disconnect()
                    return@repeat
                }
            } finally {
                if (attempt == maxRetries - 1) {
                    connection?.disconnect()
                }
            }
        }
        
        return@withContext null
    }
    
    /**
     * Extrai o link de download direto do GoFile com headers necessários
     * Segue a lógica exata do Python mantendo a sessão
     * @param url URL da página do GoFile (ex: https://gofile.io/d/I7Y2WU)
     * @param password Senha opcional se o conteúdo for protegido
     * @return GoFileDownloadInfo com URL e headers ou null se falhar
     */
    suspend fun extractDirectDownloadLink(url: String, password: String? = null): GoFileDownloadInfo? = withContext(Dispatchers.IO) {
        try {
            // Extrai content ID
            val contentId = extractContentId(url)
            if (contentId == null) {
                return@withContext null
            }
            
            // Obtém token de acesso
            val token = getAccessToken()
            if (token == null) {
                return@withContext null
            }
            
            // Faz requisição para API
            val jsonResponse = getContentInfo(contentId, token, password)
            if (jsonResponse == null || jsonResponse.optString("status") != "ok") {
                return@withContext null
            }
            
            val data = jsonResponse.optJSONObject("data")
            if (data == null) {
                return@withContext null
            }
            
            // Verifica se precisa de senha
            if (data.has("password") && data.has("passwordStatus")) {
                val passwordStatus = data.optString("passwordStatus")
                if (passwordStatus != "passwordOk") {
                    return@withContext null
                }
            }
            
            val contentType = data.optString("type")
            
            // Cria os headers necessários para o download (mantém a sessão)
            val downloadHeaders = mapOf(
                "User-Agent" to USER_AGENT,
                "Accept-Encoding" to "gzip",
                "Connection" to "keep-alive",
                "Accept" to "*/*",
                "Authorization" to "Bearer $token",
                "Cookie" to "accountToken=$token"
            )
            
            // Se for um arquivo único (não é folder)
            if (contentType != "folder") {
                val directLink = data.optString("link")
                if (directLink.isNotEmpty()) {
                    return@withContext GoFileDownloadInfo(directLink, downloadHeaders)
                }
                return@withContext null
            }
            
            // Se for uma pasta, pega o primeiro arquivo
            if (contentType == "folder") {
                val children = data.optJSONObject("children")
                if (children != null && children.length() > 0) {
                    // Itera pelos filhos para encontrar o primeiro arquivo
                    val keys = children.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val child = children.optJSONObject(key)
                        if (child != null) {
                            val childType = child.optString("type")
                            // Se for arquivo (não é folder), retorna o link com headers
                            if (childType == "file" || childType != "folder") {
                                val directLink = child.optString("link")
                                if (directLink.isNotEmpty()) {
                                    return@withContext GoFileDownloadInfo(directLink, downloadHeaders)
                                }
                            }
                        }
                    }
                }
            }
            
            return@withContext null
            
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }
    
    /**
     * Extrai o link de download direto com retry
     * @param url URL da página do GoFile
     * @param password Senha opcional
     * @param maxRetries Número máximo de tentativas
     * @return GoFileDownloadInfo ou null se falhar (retorna null para usar URL original sem headers)
     */
    suspend fun extractDirectDownloadLinkWithRetry(
        url: String, 
        password: String? = null,
        maxRetries: Int = 3
    ): GoFileDownloadInfo? {
        repeat(maxRetries) { attempt ->
            try {
                val downloadInfo = extractDirectDownloadLink(url, password)
                if (downloadInfo != null) {
                    return downloadInfo
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (attempt == maxRetries - 1) {
                    return null
                }
            }
        }
        
        // Retorna null se falhar (deixa usar URL original sem headers especiais)
        return null
    }
}
