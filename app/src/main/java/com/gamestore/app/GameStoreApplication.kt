package com.gamestore.app

import android.app.Application
import com.gamestore.app.data.local.GameDatabase
import com.gamestore.app.data.preferences.PreferencesManager
import com.gamestore.app.data.repository.GameRepository

class GameStoreApplication : Application() {
    lateinit var repository: GameRepository
    lateinit var preferencesManager: PreferencesManager

    override fun onCreate() {
        super.onCreate()
        val database = GameDatabase.getDatabase(this)
        repository = GameRepository(database.gameDao())
        preferencesManager = PreferencesManager(this)
    }
}
