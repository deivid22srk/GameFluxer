package com.gamestore.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesManager(private val context: Context) {
    
    companion object {
        private val DOWNLOAD_FOLDER_KEY = stringPreferencesKey("download_folder")
        private val CURRENT_PLATFORM_KEY = stringPreferencesKey("current_platform")
        private val PLATFORMS_JSON_KEY = stringPreferencesKey("platforms_json")
        private val GITHUB_REPO_URL_KEY = stringPreferencesKey("github_repo_url")
        private val CUSTOM_DOWNLOAD_SOURCES_KEY = stringSetPreferencesKey("custom_download_sources")
        private val INTERNET_ARCHIVE_EMAIL_KEY = stringPreferencesKey("internet_archive_email")
        private val INTERNET_ARCHIVE_PASSWORD_KEY = stringPreferencesKey("internet_archive_password")
    }
    
    val downloadFolder: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[DOWNLOAD_FOLDER_KEY]
    }
    
    val currentPlatform: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[CURRENT_PLATFORM_KEY]
    }
    
    val platformsJson: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PLATFORMS_JSON_KEY]
    }
    
    val githubRepoUrl: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[GITHUB_REPO_URL_KEY] ?: ""
    }
    
    val customDownloadSources: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[CUSTOM_DOWNLOAD_SOURCES_KEY] ?: emptySet()
    }
    
    val internetArchiveEmail: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[INTERNET_ARCHIVE_EMAIL_KEY] ?: ""
    }
    
    val internetArchivePassword: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[INTERNET_ARCHIVE_PASSWORD_KEY] ?: ""
    }
    
    suspend fun setDownloadFolder(path: String) {
        context.dataStore.edit { preferences ->
            preferences[DOWNLOAD_FOLDER_KEY] = path
        }
    }
    
    suspend fun setCurrentPlatform(platform: String) {
        context.dataStore.edit { preferences ->
            preferences[CURRENT_PLATFORM_KEY] = platform
        }
    }
    
    suspend fun setPlatformsJson(json: String) {
        context.dataStore.edit { preferences ->
            preferences[PLATFORMS_JSON_KEY] = json
        }
    }
    
    suspend fun setGitHubRepoUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[GITHUB_REPO_URL_KEY] = url
        }
    }
    
    suspend fun addCustomDownloadSource(sourceUrl: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[CUSTOM_DOWNLOAD_SOURCES_KEY] ?: emptySet()
            preferences[CUSTOM_DOWNLOAD_SOURCES_KEY] = current + sourceUrl
        }
    }
    
    suspend fun removeCustomDownloadSource(sourceUrl: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[CUSTOM_DOWNLOAD_SOURCES_KEY] ?: emptySet()
            preferences[CUSTOM_DOWNLOAD_SOURCES_KEY] = current - sourceUrl
        }
    }
    
    suspend fun clearCustomDownloadSources() {
        context.dataStore.edit { preferences ->
            preferences[CUSTOM_DOWNLOAD_SOURCES_KEY] = emptySet()
        }
    }
    
    suspend fun setInternetArchiveCredentials(email: String, password: String) {
        context.dataStore.edit { preferences ->
            preferences[INTERNET_ARCHIVE_EMAIL_KEY] = email
            preferences[INTERNET_ARCHIVE_PASSWORD_KEY] = password
        }
    }
    
    suspend fun clearInternetArchiveCredentials() {
        context.dataStore.edit { preferences ->
            preferences[INTERNET_ARCHIVE_EMAIL_KEY] = ""
            preferences[INTERNET_ARCHIVE_PASSWORD_KEY] = ""
        }
    }
}
