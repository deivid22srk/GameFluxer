package com.gamestore.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gamestore.app.GameStoreApplication
import com.gamestore.app.data.model.Game
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class GameDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as GameStoreApplication
    private val repository = app.repository

    private val _game = MutableStateFlow<Game?>(null)
    val game: StateFlow<Game?> = _game

    fun loadGame(gameId: String) {
        viewModelScope.launch {
            repository.getGameById(gameId).collectLatest { game ->
                _game.value = game
            }
        }
    }
}
