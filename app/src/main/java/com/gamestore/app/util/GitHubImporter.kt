package com.gamestore.app.util

import android.content.Context
import android.util.Log
import com.gamestore.app.data.model.DatabaseConfig
import com.gamestore.app.data.model.Game
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

class GitHubImporter(private val context: Context) {
    
    companion object {
        private const val TAG = "GitHubImporter"
        private const val DEFAULT_REPO = "https://github.com/deivid22srk/GameFluxerDB"
    }
    
    data class ImportResult(
        val success: Boolean,
        val config: DatabaseConfig? = null,
        val games: Map<String, List<Game>> = emptyMap(),
        val error: String? = null
    )

    suspend fun importFromGitHub(repoUrl: String = DEFAULT_REPO): ImportResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting import from GitHub: $repoUrl")
            
            val (owner, repo) = parseGitHubUrl(repoUrl)
            if (owner.isEmpty() || repo.isEmpty()) {
                return@withContext ImportResult(false, error = "URL do repositório inválida")
            }
            
            Log.d(TAG, "Parsed repo: $owner/$repo")
            
            val configJson = downloadFileFromGitHub(owner, repo, "config.json")
            if (configJson == null) {
                return@withContext ImportResult(false, error = "config.json não encontrado no repositório")
            }
            
            val decodedConfigJson = decodeIfBase64(configJson)
            Log.d(TAG, "Config JSON downloaded and decoded")
            
            val config = Gson().fromJson(decodedConfigJson, DatabaseConfig::class.java)
            val gamesMap = mutableMapOf<String, List<Game>>()
            
            config.platforms.forEach { platform ->
                val dbContent = downloadFileFromGitHub(owner, repo, platform.databasePath)
                if (dbContent != null) {
                    val decodedDbContent = decodeIfBase64(dbContent)
                    val gameListType = object : TypeToken<List<Game>>() {}.type
                    val games: List<Game> = Gson().fromJson(decodedDbContent, gameListType)
                    Log.d(TAG, "Loaded ${games.size} games for platform ${platform.name}")
                    gamesMap[platform.name] = games
                } else {
                    Log.w(TAG, "Database file not found: ${platform.databasePath}")
                }
            }
            
            Log.d(TAG, "Import successful. Total games: ${gamesMap.values.sumOf { it.size }}")
            return@withContext ImportResult(
                success = true,
                config = config,
                games = gamesMap
            )
        } catch (e: Exception) {
            Log.e(TAG, "Import error: ${e.message}", e)
            e.printStackTrace()
            return@withContext ImportResult(false, error = "Erro ao importar do GitHub: ${e.message}")
        }
    }
    
    private fun parseGitHubUrl(url: String): Pair<String, String> {
        try {
            val cleanUrl = url.trim().removeSuffix("/").removeSuffix(".git")
            val parts = cleanUrl.replace("https://github.com/", "")
                .replace("http://github.com/", "")
                .split("/")
            
            if (parts.size >= 2) {
                return Pair(parts[0], parts[1])
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing GitHub URL: ${e.message}")
        }
        return Pair("", "")
    }
    
    private fun downloadFileFromGitHub(owner: String, repo: String, filePath: String): String? {
        var connection: HttpURLConnection? = null
        try {
            val rawUrl = "https://raw.githubusercontent.com/$owner/$repo/main/$filePath"
            Log.d(TAG, "Downloading: $rawUrl")
            
            val url = URL(rawUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty("User-Agent", "GameFluxer/1.0")
            
            val responseCode = connection.responseCode
            if (responseCode == 404) {
                val masterUrl = "https://raw.githubusercontent.com/$owner/$repo/master/$filePath"
                Log.d(TAG, "Trying master branch: $masterUrl")
                connection.disconnect()
                
                connection = URL(masterUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.setRequestProperty("User-Agent", "GameFluxer/1.0")
            }
            
            if (connection.responseCode != 200) {
                Log.e(TAG, "Failed to download file. HTTP ${connection.responseCode}")
                return null
            }
            
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val content = reader.readText()
            reader.close()
            
            Log.d(TAG, "Downloaded file: $filePath (${content.length} bytes)")
            return content
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading file $filePath: ${e.message}")
            return null
        } finally {
            connection?.disconnect()
        }
    }
    
    private fun decodeIfBase64(content: String): String {
        try {
            val trimmed = content.trim()
            
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                return content
            }
            
            if (isBase64(trimmed)) {
                Log.d(TAG, "Detected Base64 content, decoding...")
                val decoded = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    String(Base64.getDecoder().decode(trimmed))
                } else {
                    String(android.util.Base64.decode(trimmed, android.util.Base64.DEFAULT))
                }
                Log.d(TAG, "Successfully decoded Base64")
                return decoded
            }
            
            return content
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode Base64, using original content: ${e.message}")
            return content
        }
    }
    
    private fun isBase64(str: String): Boolean {
        if (str.isEmpty()) return false
        
        val base64Pattern = "^[A-Za-z0-9+/]*={0,2}$"
        if (!str.matches(Regex(base64Pattern))) {
            return false
        }
        
        return str.length % 4 == 0 && str.length > 100
    }
}
