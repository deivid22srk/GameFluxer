package com.gamestore.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesManager(private val context: Context) {
    companion object {
        val CURRENT_PLATFORM = stringPreferencesKey("current_platform")
        val PLATFORMS_JSON = stringPreferencesKey("platforms_json")
        val DOWNLOAD_FOLDER = stringPreferencesKey("download_folder")
    }

    val currentPlatform: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[CURRENT_PLATFORM]
        }

    val platformsJson: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[PLATFORMS_JSON]
        }

    val downloadFolder: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[DOWNLOAD_FOLDER]
        }

    suspend fun setCurrentPlatform(platform: String) {
        context.dataStore.edit { preferences ->
            preferences[CURRENT_PLATFORM] = platform
        }
    }

    suspend fun setPlatformsJson(json: String) {
        context.dataStore.edit { preferences ->
            preferences[PLATFORMS_JSON] = json
        }
    }

    suspend fun setDownloadFolder(path: String) {
        context.dataStore.edit { preferences ->
            preferences[DOWNLOAD_FOLDER] = path
        }
    }
}
