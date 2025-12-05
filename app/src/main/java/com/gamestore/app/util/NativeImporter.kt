package com.gamestore.app.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.gamestore.app.data.model.DatabaseConfig
import com.gamestore.app.data.model.Game
import com.gamestore.app.data.model.Platform
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class NativeImporter(private val context: Context) {
    
    companion object {
        private const val TAG = "NativeImporter"
        
        init {
            try {
                System.loadLibrary("gamefluxer")
                Log.d(TAG, "Native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library", e)
            }
        }
    }
    
    data class ImportResult(
        val success: Boolean,
        val config: DatabaseConfig? = null,
        val games: Map<String, List<Game>> = emptyMap(),
        val error: String? = null
    )
    
    data class NativeImportResult(
        val success: Boolean,
        val totalGames: Int,
        val error: String,
        val platforms: List<NativePlatform>,
        val games: Map<String, List<NativeGame>>
    )
    
    data class NativePlatform(
        val name: String,
        val databasePath: String
    )
    
    data class NativeGame(
        val id: String,
        val name: String,
        val description: String,
        val version: String,
        val size: String,
        val rating: Float,
        val developer: String,
        val category: String,
        val platform: String,
        val iconUrl: String,
        val bannerUrl: String,
        val screenshots: String,
        val downloadUrl: String,
        val releaseDate: String
    )
    
    private external fun importFromGitHubNative(repoUrl: String): String
    private external fun importFromZipNative(zipPath: String): String
    
    suspend fun importFromGitHub(repoUrl: String): ImportResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting native GitHub import: $repoUrl")
            
            val resultJson = importFromGitHubNative(repoUrl)
            Log.d(TAG, "Native import completed, parsing result")
            
            return@withContext parseNativeResult(resultJson)
        } catch (e: Exception) {
            Log.e(TAG, "Native import error", e)
            return@withContext ImportResult(
                success = false,
                error = "Native import failed: ${e.message}"
            )
        }
    }
    
    suspend fun importFromZip(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting native ZIP import")
            
            val tempFile = File(context.cacheDir, "import_temp.zip")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            Log.d(TAG, "ZIP copied to: ${tempFile.absolutePath}")
            
            val resultJson = importFromZipNative(tempFile.absolutePath)
            Log.d(TAG, "Native import completed, parsing result")
            
            tempFile.delete()
            
            return@withContext parseNativeResult(resultJson)
        } catch (e: Exception) {
            Log.e(TAG, "Native import error", e)
            return@withContext ImportResult(
                success = false,
                error = "Native import failed: ${e.message}"
            )
        }
    }
    
    private fun parseNativeResult(json: String): ImportResult {
        try {
            val nativeResult = Gson().fromJson(json, NativeImportResult::class.java)
            
            if (!nativeResult.success) {
                return ImportResult(
                    success = false,
                    error = nativeResult.error
                )
            }
            
            val config = DatabaseConfig(
                platforms = nativeResult.platforms.map { 
                    Platform(
                        name = it.name,
                        databasePath = it.databasePath,
                        extendedDownloads = null
                    )
                }
            )
            
            val games = mutableMapOf<String, List<Game>>()
            nativeResult.games.forEach { (platform, nativeGames) ->
                games[platform] = nativeGames.map { ng ->
                    Game(
                        id = ng.id,
                        name = ng.name,
                        description = ng.description,
                        version = ng.version,
                        size = ng.size,
                        rating = ng.rating,
                        developer = ng.developer,
                        category = ng.category,
                        platform = ng.platform,
                        iconUrl = ng.iconUrl,
                        bannerUrl = ng.bannerUrl,
                        screenshots = ng.screenshots,
                        downloadUrl = ng.downloadUrl,
                        releaseDate = ng.releaseDate
                    )
                }
            }
            
            Log.d(TAG, "Successfully parsed ${nativeResult.totalGames} games")
            
            return ImportResult(
                success = true,
                config = config,
                games = games
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse native result", e)
            return ImportResult(
                success = false,
                error = "Failed to parse import result: ${e.message}"
            )
        }
    }
}
