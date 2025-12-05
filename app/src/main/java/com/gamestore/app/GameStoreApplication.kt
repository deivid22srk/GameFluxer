package com.gamestore.app

import android.app.Application
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.gamestore.app.data.local.GameDatabase
import com.gamestore.app.data.model.DatabaseConfig
import com.gamestore.app.data.model.Platform
import com.gamestore.app.data.preferences.PreferencesManager
import com.gamestore.app.data.repository.GameRepository
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File

class GameStoreApplication : Application() {
    lateinit var repository: GameRepository
    lateinit var preferencesManager: PreferencesManager

    override fun onCreate() {
        super.onCreate()
        
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
        
        val database = GameDatabase.getDatabase(this)
        repository = GameRepository(database.gameDao())
        preferencesManager = PreferencesManager(this)
        
        createDefaultDownloadFolder()
    }
    
    private fun createDefaultDownloadFolder() {
        try {
            val defaultFolder = File("/storage/emulated/0/GameFluxer")
            if (!defaultFolder.exists()) {
                defaultFolder.mkdirs()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    suspend fun getPlatformForGame(platformName: String): Platform? {
        return try {
            val json = preferencesManager.platformsJson.first()
            json?.let {
                val config = Gson().fromJson(it, DatabaseConfig::class.java)
                config.platforms.find { p -> p.name == platformName }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
