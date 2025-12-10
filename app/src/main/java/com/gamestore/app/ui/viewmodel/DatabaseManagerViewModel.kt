package com.gamestore.app.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gamestore.app.GameStoreApplication
import com.gamestore.app.data.model.DatabaseConfig
import com.gamestore.app.data.model.Game
import com.gamestore.app.data.model.Platform
import com.gamestore.app.util.DatabaseExporter
import com.gamestore.app.util.JsonImporter
import com.gamestore.app.util.ZipImporter
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DatabaseManagerViewModel(application: Application) : AndroidViewModel(application) {
    
    private val app = application as GameStoreApplication
    private val repository = app.repository
    private val preferencesManager = app.preferencesManager
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
                val configJson = preferencesManager.platformsJson.first()
                if (!configJson.isNullOrEmpty()) {
                    val config = Gson().fromJson(configJson, DatabaseConfig::class.java)
                    _currentConfig.value = config
                    
                    val storedGames = mutableMapOf<String, List<Game>>()
                    config.platforms.forEach { platform ->
                        val platformGames = repository.getGamesByPlatform(platform.name).first()
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
                            databases = emptyList(),
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
                    databases = emptyList(),
                    extendedDownloads = null
                )
                
                val updatedConfig = currentConfig.copy(
                    platforms = currentConfig.platforms + newPlatform
                )
                
                _currentConfig.value = updatedConfig
                _gamesMap.value = _gamesMap.value + (platformName to emptyList())
                
                saveCurrentDatabase()
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
                
                saveCurrentDatabase()
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
                    
                    saveCurrentDatabase()
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
                
                saveCurrentDatabase()
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
                    
                    saveCurrentDatabase()
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
                
                saveCurrentDatabase()
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
                    
                    repository.deleteAllGames()
                    result.games.forEach { (_, games) ->
                        repository.insertGames(games)
                    }
                    
                    preferencesManager.setPlatformsJson(Gson().toJson(result.config))
                    
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
                val config = _currentConfig.value
                if (config == null) {
                    return@launch
                }
                
                repository.deleteAllGames()
                _gamesMap.value.forEach { (_, games) ->
                    repository.insertGames(games)
                }
                
                preferencesManager.setPlatformsJson(Gson().toJson(config))
                
                if (config.platforms.isNotEmpty()) {
                    val currentPlatform = preferencesManager.currentPlatform.first()
                    if (currentPlatform == null || !config.platforms.any { it.name == currentPlatform }) {
                        preferencesManager.setCurrentPlatform(config.platforms[0].name)
                    }
                }
            } catch (e: Exception) {
                _statusMessage.value = "Erro ao salvar: ${e.message}"
            }
        }
    }
    
    fun addDatabaseToPlatform(platformName: String, databaseName: String, databasePath: String) {
        viewModelScope.launch {
            try {
                val currentConfig = _currentConfig.value ?: return@launch
                
                val updatedPlatforms = currentConfig.platforms.map { platform ->
                    if (platform.name == platformName) {
                        val newDatabase = com.gamestore.app.data.model.DatabaseEntry(
                            name = databaseName,
                            path = databasePath
                        )
                        platform.copy(databases = platform.databases + newDatabase)
                    } else {
                        platform
                    }
                }
                
                _currentConfig.value = currentConfig.copy(platforms = updatedPlatforms)
                saveCurrentDatabase()
                _statusMessage.value = "Database '$databaseName' adicionado à plataforma '$platformName'!"
            } catch (e: Exception) {
                _statusMessage.value = "Erro ao adicionar database: ${e.message}"
            }
        }
    }
    
    fun removeDatabaseFromPlatform(platformName: String, databaseName: String) {
        viewModelScope.launch {
            try {
                val currentConfig = _currentConfig.value ?: return@launch
                
                val updatedPlatforms = currentConfig.platforms.map { platform ->
                    if (platform.name == platformName) {
                        platform.copy(databases = platform.databases.filter { it.name != databaseName })
                    } else {
                        platform
                    }
                }
                
                _currentConfig.value = currentConfig.copy(platforms = updatedPlatforms)
                
                val databaseKey = "$platformName:$databaseName"
                _gamesMap.value = _gamesMap.value.filterKeys { it != databaseKey }
                
                saveCurrentDatabase()
                _statusMessage.value = "Database '$databaseName' removido da plataforma '$platformName'!"
            } catch (e: Exception) {
                _statusMessage.value = "Erro ao remover database: ${e.message}"
            }
        }
    }
    
    fun importJsonForDatabase(platformName: String, databaseName: String, uri: Uri) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                val result = jsonImporter.importJsonFile(uri)
                
                if (result.success) {
                    val updatedGames = _gamesMap.value.toMutableMap()
                    
                    val gamesWithPlatform = result.games.map { game ->
                        game.copy(platform = "$platformName:$databaseName")
                    }
                    
                    val databaseKey = "$platformName:$databaseName"
                    updatedGames[databaseKey] = gamesWithPlatform
                    _gamesMap.value = updatedGames
                    
                    saveCurrentDatabase()
                    _statusMessage.value = "${result.games.size} jogos importados para '$platformName - $databaseName'!"
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
    
    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}