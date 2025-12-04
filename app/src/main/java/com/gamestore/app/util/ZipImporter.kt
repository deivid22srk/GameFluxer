package com.gamestore.app.util

import android.content.Context
import android.net.Uri
import com.gamestore.app.data.model.DatabaseConfig
import com.gamestore.app.data.model.Game
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.*
import java.util.zip.ZipInputStream

class ZipImporter(private val context: Context) {
    
    data class ImportResult(
        val success: Boolean,
        val config: DatabaseConfig? = null,
        val games: Map<String, List<Game>> = emptyMap(),
        val error: String? = null
    )

    suspend fun importZipFile(uri: Uri): ImportResult {
        try {
            val tempDir = File(context.cacheDir, "import_temp")
            if (tempDir.exists()) {
                tempDir.deleteRecursively()
            }
            tempDir.mkdirs()

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                extractZip(inputStream, tempDir)
            } ?: return ImportResult(false, error = "Failed to open ZIP file")

            val configFile = File(tempDir, "config.json")
            if (!configFile.exists()) {
                return ImportResult(false, error = "config.json not found in ZIP")
            }

            val configJson = configFile.readText()
            val config = Gson().fromJson(configJson, DatabaseConfig::class.java)

            val gamesMap = mutableMapOf<String, List<Game>>()
            
            config.platforms.forEach { platform ->
                val dbFile = File(tempDir, platform.databasePath)
                if (dbFile.exists()) {
                    val gamesJson = dbFile.readText()
                    val gameListType = object : TypeToken<List<Game>>() {}.type
                    val games: List<Game> = Gson().fromJson(gamesJson, gameListType)
                    gamesMap[platform.name] = games
                }
            }

            tempDir.deleteRecursively()

            return ImportResult(
                success = true,
                config = config,
                games = gamesMap
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return ImportResult(false, error = e.message ?: "Unknown error")
        }
    }

    private fun extractZip(inputStream: InputStream, targetDir: File) {
        ZipInputStream(BufferedInputStream(inputStream)).use { zipInputStream ->
            var entry = zipInputStream.nextEntry
            while (entry != null) {
                val file = File(targetDir, entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { output ->
                        zipInputStream.copyTo(output)
                    }
                }
                zipInputStream.closeEntry()
                entry = zipInputStream.nextEntry
            }
        }
    }
}
