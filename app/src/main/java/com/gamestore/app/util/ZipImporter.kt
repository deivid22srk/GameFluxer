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

            val configFile = File(tempDir, "config.json")
            if (!configFile.exists()) {
                Log.e(TAG, "config.json not found in ZIP")
                return ImportResult(false, error = "config.json não encontrado no arquivo ZIP")
            }

            val configJson = configFile.readText()
            Log.d(TAG, "Config JSON: $configJson")
            val config = Gson().fromJson(configJson, DatabaseConfig::class.java)

            val gamesMap = mutableMapOf<String, List<Game>>()
            
            config.platforms.forEach { platform ->
                val dbFile = File(tempDir, platform.databasePath)
                Log.d(TAG, "Looking for database: ${platform.databasePath} at ${dbFile.absolutePath}")
                if (dbFile.exists()) {
                    val gamesJson = dbFile.readText()
                    val gameListType = object : TypeToken<List<Game>>() {}.type
                    val games: List<Game> = Gson().fromJson(gamesJson, gameListType)
                    Log.d(TAG, "Loaded ${games.size} games for platform ${platform.name}")
                    gamesMap[platform.name] = games
                } else {
                    Log.w(TAG, "Database file not found: ${dbFile.absolutePath}")
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

    private fun extractZip(inputStream: InputStream, targetDir: File) {
        ZipInputStream(BufferedInputStream(inputStream)).use { zipInputStream ->
            var entry = zipInputStream.nextEntry
            while (entry != null) {
                val file = File(targetDir, entry.name)
                Log.d(TAG, "Extracting: ${entry.name}")
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
}
