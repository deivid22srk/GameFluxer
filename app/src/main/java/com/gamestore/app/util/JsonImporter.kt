package com.gamestore.app.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.gamestore.app.data.model.Game
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class JsonImporter(private val context: Context) {
    
    companion object {
        private const val TAG = "JsonImporter"
    }
    
    data class ImportResult(
        val success: Boolean,
        val games: List<Game> = emptyList(),
        val error: String? = null
    )

    suspend fun importJsonFile(uri: Uri): ImportResult {
        try {
            Log.d(TAG, "Starting import from JSON URI: $uri")
            
            val jsonContent = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader().use { it.readText() }
            } ?: run {
                Log.e(TAG, "Failed to open JSON file")
                return ImportResult(false, error = "Falha ao abrir o arquivo JSON")
            }
            
            val gameListType = object : TypeToken<List<Game>>() {}.type
            val games: List<Game> = Gson().fromJson(jsonContent, gameListType)
            
            Log.d(TAG, "Import successful. Loaded ${games.size} games")
            return ImportResult(success = true, games = games)
        } catch (e: Exception) {
            Log.e(TAG, "Import error: ${e.message}", e)
            return ImportResult(false, error = "Erro ao importar JSON: ${e.message}")
        }
    }
}