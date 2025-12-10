package com.gamestore.app.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.gamestore.app.data.model.DatabaseConfig
import com.gamestore.app.data.model.Game
import com.gamestore.app.data.model.Platform
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DatabaseExporter(private val context: Context) {
    
    companion object {
        private const val TAG = "DatabaseExporter"
    }
    
    data class ExportResult(
        val success: Boolean,
        val filePath: String? = null,
        val error: String? = null
    )

    suspend fun exportToZip(
        config: DatabaseConfig,
        gamesMap: Map<String, List<Game>>,
        outputUri: Uri
    ): ExportResult {
        try {
            Log.d(TAG, "Starting export to ZIP")
            
            val tempDir = File(context.cacheDir, "export_temp")
            if (tempDir.exists()) {
                tempDir.deleteRecursively()
            }
            tempDir.mkdirs()
            
            val gson = GsonBuilder().setPrettyPrinting().create()
            
            val configFile = File(tempDir, "config.json")
            configFile.writeText(gson.toJson(config))
            Log.d(TAG, "Config file created")
            
            val databasesDir = File(tempDir, "databases")
            databasesDir.mkdirs()
            
            config.platforms.forEach { platform ->
                val games = gamesMap[platform.name] ?: emptyList()
                val dbPath = platform.databasePath.substringAfter("databases/")
                val dbFile = File(databasesDir, dbPath)
                dbFile.writeText(gson.toJson(games))
                Log.d(TAG, "Created database file for ${platform.name}: ${games.size} games")
            }
            
            context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                ZipOutputStream(BufferedOutputStream(outputStream)).use { zipOut ->
                    zipDirectory(tempDir, tempDir.name, zipOut)
                }
            } ?: run {
                Log.e(TAG, "Failed to open output stream")
                return ExportResult(false, error = "Falha ao criar arquivo ZIP")
            }
            
            tempDir.deleteRecursively()
            
            Log.d(TAG, "Export successful")
            return ExportResult(success = true, filePath = outputUri.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Export error: ${e.message}", e)
            return ExportResult(false, error = "Erro ao exportar: ${e.message}")
        }
    }
    
    private fun zipDirectory(directory: File, baseName: String, zipOut: ZipOutputStream) {
        directory.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                zipDirectory(file, "$baseName/${file.name}", zipOut)
            } else {
                val entryName = "$baseName/${file.name}"
                Log.d(TAG, "Adding to ZIP: $entryName")
                val entry = ZipEntry(entryName)
                zipOut.putNextEntry(entry)
                FileInputStream(file).use { input ->
                    input.copyTo(zipOut)
                }
                zipOut.closeEntry()
            }
        }
    }
    
    fun createEmptyDatabase(platformName: String): DatabaseConfig {
        return DatabaseConfig(
            platforms = listOf(
                Platform(
                    name = platformName,
                    databasePath = "databases/${platformName.lowercase().replace(" ", "_")}_games.json",
                    extendedDownloads = null
                )
            )
        )
    }
}