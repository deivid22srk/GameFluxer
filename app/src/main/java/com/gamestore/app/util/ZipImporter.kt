package com.gamestore.app.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.gamestore.app.data.model.DatabaseConfig
import com.gamestore.app.data.model.Game
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.*
import java.util.zip.ZipInputStream
import java.util.Base64

class ZipImporter(private val context: Context) {
    
    companion object {
        private const val TAG = "ZipImporter"
    }
    
    data class ImportResult(
        val success: Boolean,
        val config: DatabaseConfig? = null,
        val games: Map<String, List<Game>> = emptyMap(),
        val error: String? = null
    )

    suspend fun importZipFile(uri: Uri): ImportResult {
        try {
            Log.d(TAG, "Starting import from URI: $uri")
            val tempDir = File(context.cacheDir, "import_temp")
            if (tempDir.exists()) {
                tempDir.deleteRecursively()
            }
            tempDir.mkdirs()
            Log.d(TAG, "Temp directory created: ${tempDir.absolutePath}")

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                extractZip(inputStream, tempDir)
            } ?: run {
                Log.e(TAG, "Failed to open ZIP file")
                return ImportResult(false, error = "Falha ao abrir o arquivo ZIP")
            }

            val extractedFiles = tempDir.listFiles()
            Log.d(TAG, "Extracted files: ${extractedFiles?.map { it.name }?.joinToString()}")

            val configFile = findConfigFile(tempDir)
            if (configFile == null || !configFile.exists()) {
                Log.e(TAG, "config.json not found in ZIP. Searched in: ${tempDir.absolutePath}")
                listAllFiles(tempDir)
                return ImportResult(false, error = "config.json não encontrado no arquivo ZIP. Verifique se o arquivo está na raiz do ZIP.")
            }

            Log.d(TAG, "Found config.json at: ${configFile.absolutePath}")
            val baseDir = configFile.parentFile ?: tempDir

            val configJson = decodeIfBase64(configFile.readText())
            Log.d(TAG, "Config JSON: $configJson")
            val config = Gson().fromJson(configJson, DatabaseConfig::class.java)

            val gamesMap = mutableMapOf<String, List<Game>>()
            
            config.platforms.forEach { platform ->
                val dbFile = File(baseDir, platform.databasePath)
                Log.d(TAG, "Looking for database: ${platform.databasePath} at ${dbFile.absolutePath}")
                if (dbFile.exists()) {
                    val gamesJson = decodeIfBase64(dbFile.readText())
                    val gameListType = object : TypeToken<List<Game>>() {}.type
                    val games: List<Game> = Gson().fromJson(gamesJson, gameListType)
                    Log.d(TAG, "Loaded ${games.size} games for platform ${platform.name}")
                    gamesMap[platform.name] = games
                } else {
                    Log.w(TAG, "Database file not found: ${dbFile.absolutePath}")
                }
                
                platform.databases.forEach { database ->
                    val secondaryDbFile = File(baseDir, database.path)
                    Log.d(TAG, "Looking for secondary database: ${database.path} at ${secondaryDbFile.absolutePath}")
                    if (secondaryDbFile.exists()) {
                        val gamesJson = decodeIfBase64(secondaryDbFile.readText())
                        val gameListType = object : TypeToken<List<Game>>() {}.type
                        val games: List<Game> = Gson().fromJson(gamesJson, gameListType)
                        Log.d(TAG, "Loaded ${games.size} games for secondary database ${platform.name}:${database.name}")
                        gamesMap["${platform.name}:${database.name}"] = games
                    } else {
                        Log.w(TAG, "Secondary database file not found: ${secondaryDbFile.absolutePath}")
                    }
                }
            }

            tempDir.deleteRecursively()

            Log.d(TAG, "Import successful. Total games: ${gamesMap.values.sumOf { it.size }}")
            return ImportResult(
                success = true,
                config = config,
                games = gamesMap
            )
        } catch (e: Exception) {
            Log.e(TAG, "Import error: ${e.message}", e)
            e.printStackTrace()
            return ImportResult(false, error = "Erro ao importar: ${e.message ?: "Erro desconhecido"}")
        }
    }

    private fun findConfigFile(directory: File): File? {
        directory.listFiles()?.forEach { file ->
            if (file.isFile && file.name == "config.json") {
                return file
            } else if (file.isDirectory) {
                val found = findConfigFile(file)
                if (found != null) return found
            }
        }
        return null
    }

    private fun listAllFiles(directory: File, prefix: String = "") {
        directory.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                Log.d(TAG, "$prefix[DIR] ${file.name}/")
                listAllFiles(file, "$prefix  ")
            } else {
                Log.d(TAG, "$prefix[FILE] ${file.name} (${file.length()} bytes)")
            }
        }
    }

    private fun extractZip(inputStream: InputStream, targetDir: File) {
        ZipInputStream(BufferedInputStream(inputStream)).use { zipInputStream ->
            var entry = zipInputStream.nextEntry
            while (entry != null) {
                val entryName = entry.name.replace("\\", "/")
                val file = File(targetDir, entryName)
                Log.d(TAG, "Extracting: $entryName")
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { output ->
                        zipInputStream.copyTo(output)
                    }
                    Log.d(TAG, "Extracted file: ${file.absolutePath}, size: ${file.length()} bytes")
                }
                zipInputStream.closeEntry()
                entry = zipInputStream.nextEntry
            }
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
