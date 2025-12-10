package com.gamestore.app.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gamestore.app.GameStoreApplication
import com.gamestore.app.data.model.DatabaseConfig
import com.gamestore.app.data.model.Game
import com.gamestore.app.data.model.Platform
import com.gamestore.app.util.ZipImporter
import com.gamestore.app.util.GitHubImporter
import com.google.gson.Gson
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as GameStoreApplication
    private val repository = app.repository
    private val preferencesManager = app.preferencesManager
    private val zipImporter = ZipImporter(application)
    private val githubImporter = GitHubImporter(application)

    private val _currentPlatform = MutableStateFlow<String?>(null)
    val currentPlatform: StateFlow<String?> = _currentPlatform
    
    private val _currentDatabase = MutableStateFlow<String?>(null)
    val currentDatabase: StateFlow<String?> = _currentDatabase

    private val _platforms = MutableStateFlow<List<String>>(emptyList())
    val platforms: StateFlow<List<String>> = _platforms
    
    private val _databaseConfig = MutableStateFlow<DatabaseConfig?>(null)
    val databaseConfig: StateFlow<DatabaseConfig?> = _databaseConfig

    private val _games = MutableStateFlow<List<Game>>(emptyList())
    val games: StateFlow<List<Game>> = _games

    private val _categories = MutableStateFlow<List<String>>(emptyList())
    val categories: StateFlow<List<String>> = _categories

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _importStatus = MutableStateFlow<String?>(null)
    val importStatus: StateFlow<String?> = _importStatus

    val githubRepoUrl: StateFlow<String> = preferencesManager.githubRepoUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    
    val customDownloadSources: StateFlow<Set<String>> = preferencesManager.customDownloadSources
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    
    val internetArchiveEmail: StateFlow<String> = preferencesManager.internetArchiveEmail
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    
    val internetArchivePassword: StateFlow<String> = preferencesManager.internetArchivePassword
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    
    private val _currentPlatformData = MutableStateFlow<Platform?>(null)
    val currentPlatformData: StateFlow<Platform?> = _currentPlatformData

    init {
        viewModelScope.launch {
            preferencesManager.currentPlatform.collectLatest { platform ->
                _currentPlatform.value = platform
                platform?.let { 
                    loadGames(it)
                    loadCurrentPlatformData(it)
                }
            }
        }

        viewModelScope.launch {
            preferencesManager.platformsJson.collectLatest { json ->
                json?.let {
                    try {
                        val config = Gson().fromJson(it, DatabaseConfig::class.java)
                        _platforms.value = config.platforms.map { p -> p.name }
                        _databaseConfig.value = config
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
        
        viewModelScope.launch {
            preferencesManager.currentDatabase.collectLatest { database ->
                _currentDatabase.value = database
            }
        }
    }

    fun importDatabase(uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            _importStatus.value = null
            
            val result = zipImporter.importZipFile(uri)
            
            if (result.success && result.config != null) {
                repository.deleteAllGames()
                
                result.games.forEach { (platform, games) ->
                    repository.insertGames(games)
                }
                
                val configJson = Gson().toJson(result.config)
                preferencesManager.setPlatformsJson(configJson)
                
                if (result.config.platforms.isNotEmpty()) {
                    val firstPlatform = result.config.platforms[0].name
                    preferencesManager.setCurrentPlatform(firstPlatform)
                }
                
                _importStatus.value = "Banco de dados importado com sucesso"
            } else {
                _importStatus.value = "Erro ao importar: ${result.error}"
            }
            
            _isLoading.value = false
        }
    }

    fun importFromGitHub(repoUrl: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            _importStatus.value = "Baixando do GitHub..."
            
            val url = repoUrl ?: githubRepoUrl.value
            val result = githubImporter.importFromGitHub(url)
            
            if (result.success && result.config != null) {
                repository.deleteAllGames()
                
                result.games.forEach { (platform, games) ->
                    repository.insertGames(games)
                }
                
                val configJson = Gson().toJson(result.config)
                preferencesManager.setPlatformsJson(configJson)
                
                if (result.config.platforms.isNotEmpty()) {
                    val firstPlatform = result.config.platforms[0].name
                    preferencesManager.setCurrentPlatform(firstPlatform)
                }
                
                _importStatus.value = "Banco de dados do GitHub importado com sucesso!"
            } else {
                _importStatus.value = "Erro ao importar do GitHub: ${result.error}"
            }
            
            _isLoading.value = false
        }
    }

    fun setGitHubRepoUrl(url: String) {
        viewModelScope.launch {
            preferencesManager.setGitHubRepoUrl(url)
        }
    }

    private fun loadGames(platform: String) {
        viewModelScope.launch {
            repository.getGamesByPlatform(platform).collectLatest { gameList ->
                _games.value = gameList
            }
        }

        viewModelScope.launch {
            repository.getCategories(platform).collectLatest { categoryList ->
                _categories.value = categoryList
            }
        }
    }

    fun changePlatform(platform: String) {
        viewModelScope.launch {
            preferencesManager.setCurrentPlatform(platform)
        }
    }

    fun searchGames(query: String) {
        val platform = _currentPlatform.value ?: return
        viewModelScope.launch {
            if (query.isEmpty()) {
                repository.getGamesByPlatform(platform).collectLatest { gameList ->
                    _games.value = gameList
                }
            } else {
                repository.searchGames(platform, query).collectLatest { gameList ->
                    _games.value = gameList
                }
            }
        }
    }

    fun filterByCategory(category: String) {
        val platform = _currentPlatform.value ?: return
        viewModelScope.launch {
            repository.getGamesByCategory(platform, category).collectLatest { gameList ->
                _games.value = gameList
            }
        }
    }

    fun clearImportStatus() {
        _importStatus.value = null
    }
    
    fun addCustomDownloadSource(url: String) {
        viewModelScope.launch {
            preferencesManager.addCustomDownloadSource(url)
        }
    }
    
    fun removeCustomDownloadSource(url: String) {
        viewModelScope.launch {
            preferencesManager.removeCustomDownloadSource(url)
        }
    }
    
    fun setInternetArchiveCredentials(email: String, password: String) {
        viewModelScope.launch {
            preferencesManager.setInternetArchiveCredentials(email, password)
        }
    }
    
    fun clearInternetArchiveCredentials() {
        viewModelScope.launch {
            preferencesManager.clearInternetArchiveCredentials()
        }
    }
    
    private fun loadCurrentPlatformData(platformName: String) {
        viewModelScope.launch {
            _currentPlatformData.value = app.getPlatformForGame(platformName)
        }
    }
    
    fun changePlatformWithDatabase(platform: String, databaseName: String?) {
        viewModelScope.launch {
            preferencesManager.setCurrentPlatform(platform)
            databaseName?.let {
                preferencesManager.setCurrentDatabase(it)
            }
        }
    }
    
    fun getDatabasesForPlatform(platformName: String): List<com.gamestore.app.data.model.DatabaseEntry> {
        val config = _databaseConfig.value ?: return emptyList()
        return config.platforms.find { it.name == platformName }?.databases ?: emptyList()
    }
}
