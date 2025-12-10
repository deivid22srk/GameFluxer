package com.gamestore.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gamestore.app.GameStoreApplication
import com.gamestore.app.data.model.Game
import com.gamestore.app.data.model.Platform
import com.gamestore.app.util.ExternalDownloadFetcher
import com.gamestore.app.util.ExternalDownloadMatch
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class GameDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as GameStoreApplication
    private val repository = app.repository

    private val _game = MutableStateFlow<Game?>(null)
    val game: StateFlow<Game?> = _game
    
    private val _externalDownloads = MutableStateFlow<List<ExternalDownloadMatch>>(emptyList())
    val externalDownloads: StateFlow<List<ExternalDownloadMatch>> = _externalDownloads
    
    private val _isLoadingExternal = MutableStateFlow(false)
    val isLoadingExternal: StateFlow<Boolean> = _isLoadingExternal

    fun loadGame(gameId: String) {
        viewModelScope.launch {
            repository.getGameById(gameId).collectLatest { game ->
                _game.value = game
                game?.let { loadExternalDownloads(it) }
            }
        }
    }
    
    private fun loadExternalDownloads(game: Game) {
        viewModelScope.launch {
            try {
                _isLoadingExternal.value = true
                
                val platform = app.getPlatformForGame(game.platform)
                val customSources = app.preferencesManager.customDownloadSources.first()
                
                if (platform?.extendedDownloads?.enabled == true || customSources.isNotEmpty()) {
                    val matches = ExternalDownloadFetcher.fetchExternalDownloads(
                        gameName = game.name,
                        platform = platform ?: Platform(game.platform, ""),
                        customSources = customSources
                    )
                    _externalDownloads.value = matches
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _externalDownloads.value = emptyList()
            } finally {
                _isLoadingExternal.value = false
            }
        }
    }
}
