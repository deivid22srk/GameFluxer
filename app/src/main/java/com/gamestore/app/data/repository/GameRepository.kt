package com.gamestore.app.data.repository

import com.gamestore.app.data.local.GameDao
import com.gamestore.app.data.model.Game
import kotlinx.coroutines.flow.Flow

class GameRepository(private val gameDao: GameDao) {
    fun getGamesByPlatform(platform: String): Flow<List<Game>> = 
        gameDao.getGamesByPlatform(platform)

    fun getGameById(gameId: String): Flow<Game?> = 
        gameDao.getGameById(gameId)

    fun searchGames(platform: String, query: String): Flow<List<Game>> = 
        gameDao.searchGames(platform, query)

    fun getCategories(platform: String): Flow<List<String>> = 
        gameDao.getCategories(platform)

    fun getGamesByCategory(platform: String, category: String): Flow<List<Game>> = 
        gameDao.getGamesByCategory(platform, category)

    suspend fun insertGames(games: List<Game>) = 
        gameDao.insertGames(games)

    suspend fun deleteGamesByPlatform(platform: String) = 
        gameDao.deleteGamesByPlatform(platform)

    suspend fun deleteAllGames() = 
        gameDao.deleteAllGames()
}
