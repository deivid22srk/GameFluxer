package com.gamestore.app.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gamestore.app.data.model.DatabaseConfig
import com.gamestore.app.data.model.Game
import com.gamestore.app.data.model.Platform
import com.gamestore.app.data.preferences.PreferencesManager
import com.gamestore.app.util.DatabaseExporter
import com.gamestore.app.util.JsonImporter
import com.gamestore.app.util.ZipImporter
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DatabaseManagerViewModel(application: Application) : AndroidViewModel(application) {
    
    private val preferencesManager = PreferencesManager(application)
    private val zipImporter = ZipImporter(application)
    private val jsonImporter = JsonImporter(application)
    private val databaseExporter = DatabaseExporter(application)
    
    private val _currentConfig = MutableStateFlow<DatabaseConfig?>(null)
    val currentConfig: StateFlow<DatabaseConfig?> = _currentConfig
    
    private val _gamesMap = MutableStateFlow<Map<String, List<Game>>>(emptyMap())
    val gamesMap: StateFlow<Map<String, List<Game>>> = _gamesMap
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage
    
    init {
        loadCurrentDatabase()
    }
    
    private fun loadCurrentDatabase() {
        viewModelScope.launch {
            try {
                val configJson = preferencesManager.databaseConfig.first()
                if (configJson.isNotEmpty()) {
                    val config = Gson().fromJson(configJson, DatabaseConfig::class.java)
                    _currentConfig.value = config
                    
                    val storedGames = mutableMapOf<String, List<Game>>()
                    config.platforms.forEach { platform ->
                        val platformGames = preferencesManager.getGamesForPlatform(platform.name).first()
                        storedGames[platform.name] = platformGames
                    }
                    _gamesMap.value = storedGames
                }
            } catch (e: Exception) {
                _statusMessage.value = "Erro ao carregar banco de dados atual: ${e.message}"
            }
        }
    }
    
    fun createNewDatabase(platformName: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                val newConfig = DatabaseConfig(
                    platforms = listOf(
                        Platform(
                            name = platformName,
                            databasePath = "databases/${platformName.lowercase().replace(" ", "_")}_games.json",
                            extendedDownloads = null
                        )
                    )
                )
                
                _currentConfig.value = newConfig
                _gamesMap.value = mapOf(platformName to emptyList())
                
                _statusMessage.value = "Banco de dados '$platformName' criado com sucesso!"
                _isLoading.value = false
            } catch (e: Exception) {
                _statusMessage.value = "Erro ao criar banco de dados: ${e.message}"
                _isLoading.value = false
            }
        }
    }
    
    fun addPlatform(platformName: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                val currentConfig = _currentConfig.value ?: DatabaseConfig(platforms = emptyList())
                
                if (currentConfig.platforms.any { it.name == platformName }) {
                    _statusMessage.value = "Plataforma '$platformName' já existe!"
                    _isLoading.value = false
                    return@launch
                }
                
                val newPlatform = Platform(
                    name = platformName,
                    databasePath = "databases/${platformName.lowercase().replace(" ", "_")}_games.json",
                    extendedDownloads = null
                )
                
                val updatedConfig = currentConfig.copy(
                    platforms = currentConfig.platforms + newPlatform
                )
                
                _currentConfig.value = updatedConfig
                _gamesMap.value = _gamesMap.value + (platformName to emptyList())
                
                _statusMessage.value = "Plataforma '$platformName' adicionada!"
                _isLoading.value = false
            } catch (e: Exception) {
                _statusMessage.value = "Erro ao adicionar plataforma: ${e.message}"
                _isLoading.value = false
            }
        }
    }
    
    fun removePlatform(platformName: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                val currentConfig = _currentConfig.value ?: return@launch
                
                val updatedConfig = currentConfig.copy(
                    platforms = currentConfig.platforms.filter { it.name != platformName }
                )
                
                _currentConfig.value = updatedConfig
                _gamesMap.value = _gamesMap.value.filterKeys { it != platformName }
                
                _statusMessage.value = "Plataforma '$platformName' removida!"
                _isLoading.value = false
            } catch (e: Exception) {
                _statusMessage.value = "Erro ao remover plataforma: ${e.message}"
                _isLoading.value = false
            }
        }
    }
    
    fun importJsonForPlatform(platformName: String, uri: Uri) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                val result = jsonImporter.importJsonFile(uri)
                
                if (result.success) {
                    val updatedGames = _gamesMap.value.toMutableMap()
                    
                    val gamesWithPlatform = result.games.map { game ->
                        game.copy(platform = platformName)
                    }
                    
                    updatedGames[platformName] = gamesWithPlatform
                    _gamesMap.value = updatedGames
                    
                    _statusMessage.value = "${result.games.size} jogos importados para '$platformName'!"
                } else {
                    _statusMessage.value = result.error ?: "Erro ao importar JSON"
                }
                
                _isLoading.value = false
            } catch (e: Exception) {
                _statusMessage.value = "Erro ao importar JSON: ${e.message}"
                _isLoading.value = false
            }
        }
    }
    
    fun addGame(platformName: String, game: Game) {
        viewModelScope.launch {
            try {
                val currentGames = _gamesMap.value[platformName] ?: emptyList()
                val gameWithPlatform = game.copy(platform = platformName)
                
                val updatedGames = _gamesMap.value.toMutableMap()
                updatedGames[platformName] = currentGames + gameWithPlatform
                _gamesMap.value = updatedGames
                
                _statusMessage.value = "Jogo '${game.name}' adicionado!"
            } catch (e: Exception) {
                _statusMessage.value = "Erro ao adicionar jogo: ${e.message}"
            }
        }
    }
    
    fun updateGame(platformName: String, gameId: String, updatedGame: Game) {
        viewModelScope.launch {
            try {
                val currentGames = _gamesMap.value[platformName] ?: emptyList()
                val gameIndex = currentGames.indexOfFirst { it.id == gameId }
                
                if (gameIndex >= 0) {
                    val updatedGames = _gamesMap.value.toMutableMap()
                    updatedGames[platformName] = currentGames.toMutableList().apply {
                        set(gameIndex, updatedGame.copy(platform = platformName))
                    }
                    _gamesMap.value = updatedGames
                    
                    _statusMessage.value = "Jogo '${updatedGame.name}' atualizado!"
                } else {
                    _statusMessage.value = "Jogo não encontrado!"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Erro ao atualizar jogo: ${e.message}"
            }
        }
    }
    
    fun deleteGame(platformName: String, gameId: String) {
        viewModelScope.launch {
            try {
                val currentGames = _gamesMap.value[platformName] ?: emptyList()
                val updatedGames = _gamesMap.value.toMutableMap()
                updatedGames[platformName] = currentGames.filter { it.id != gameId }
                _gamesMap.value = updatedGames
                
                _statusMessage.value = "Jogo removido!"
            } catch (e: Exception) {
                _statusMessage.value = "Erro ao remover jogo: ${e.message}"
            }
        }
    }
    
    fun exportDatabase(outputUri: Uri) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                val config = _currentConfig.value
                if (config == null) {
                    _statusMessage.value = "Nenhum banco de dados para exportar"
                    _isLoading.value = false
                    return@launch
                }
                
                val result = databaseExporter.exportToZip(config, _gamesMap.value, outputUri)
                
                if (result.success) {
                    _statusMessage.value = "Banco de dados exportado com sucesso!"
                } else {
                    _statusMessage.value = result.error ?: "Erro ao exportar"
                }
                
                _isLoading.value = false
            } catch (e: Exception) {
                _statusMessage.value = "Erro ao exportar: ${e.message}"
                _isLoading.value = false
            }
        }
    }
    
    fun importDatabase(uri: Uri) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                val result = zipImporter.importZipFile(uri)
                
                if (result.success && result.config != null) {
                    _currentConfig.value = result.config
                    _gamesMap.value = result.games
                    
                    preferencesManager.saveDatabaseConfig(Gson().toJson(result.config))
                    result.games.forEach { (platform, games) ->
                        preferencesManager.saveGamesForPlatform(platform, games)
                    }
                    
                    val totalGames = result.games.values.sumOf { it.size }
                    _statusMessage.value = "Importado: ${result.games.size} plataformas, $totalGames jogos"
                } else {
                    _statusMessage.value = result.error ?: "Erro ao importar"
                }
                
                _isLoading.value = false
            } catch (e: Exception) {
                _statusMessage.value = "Erro ao importar: ${e.message}"
                _isLoading.value = false
            }
        }
    }
    
    fun saveCurrentDatabase() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                val config = _currentConfig.value
                if (config == null) {
                    _statusMessage.value = "Nenhum banco de dados para salvar"
                    _isLoading.value = false
                    return@launch
                }
                
                preferencesManager.saveDatabaseConfig(Gson().toJson(config))
                _gamesMap.value.forEach { (platform, games) ->
                    preferencesManager.saveGamesForPlatform(platform, games)
                }
                
                _statusMessage.value = "Banco de dados salvo com sucesso!"
                _isLoading.value = false
            } catch (e: Exception) {
                _statusMessage.value = "Erro ao salvar: ${e.message}"
                _isLoading.value = false
            }
        }
    }
    
    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}