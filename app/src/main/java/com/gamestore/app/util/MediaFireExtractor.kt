package com.gamestore.app.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

object MediaFireExtractor {

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.5481.178 Safari/537.36"
    
    /**
     * Verifica se a URL é do MediaFire
     */
    fun isMediaFireUrl(url: String): Boolean {
        return url.contains("mediafire.com", ignoreCase = true)
    }
    
    /**
     * Extrai o link de download direto do MediaFire
     * @param url URL da página do MediaFire
     * @return URL de download direto ou null se falhar
     */
    suspend fun extractDirectDownloadLink(url: String): String? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        
        try {
            val urlObj = URL(url)
            connection = urlObj.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.connect()
            
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                return@withContext null
            }
            
            // Lê o conteúdo da página
            val content = BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                reader.readText()
            }
            
            // Procura pelo link de download usando regex
            // O MediaFire usa links no formato: http://download*.mediafire.com/...
            val pattern = Pattern.compile("href=\"((http|https)://download[^\"]+)\"")
            val matcher = pattern.matcher(content)
            
            if (matcher.find()) {
                return@withContext matcher.group(1)
            }
            
            // Tenta outro padrão comum do MediaFire
            val alternativePattern = Pattern.compile("\"(https?://download[0-9]+\\.mediafire\\.com/[^\"]+)\"")
            val alternativeMatcher = alternativePattern.matcher(content)
            
            if (alternativeMatcher.find()) {
                return@withContext alternativeMatcher.group(1)
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
     * @param url URL da página do MediaFire
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
                if (directLink != null) {
                    return directLink
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (attempt == maxRetries - 1) {
                    // Última tentativa falhou, retorna URL original
                    return url
                }
            }
        }
        
        // Se todas as tentativas falharem, retorna a URL original
        return url
    }
}
