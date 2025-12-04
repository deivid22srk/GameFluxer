package com.gamestore.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gamestore.app.data.model.Game
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query("SELECT * FROM games WHERE platform = :platform ORDER BY name ASC")
    fun getGamesByPlatform(platform: String): Flow<List<Game>>

    @Query("SELECT * FROM games WHERE id = :gameId")
    fun getGameById(gameId: String): Flow<Game?>

    @Query("SELECT * FROM games WHERE platform = :platform AND (name LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%' OR developer LIKE '%' || :query || '%')")
    fun searchGames(platform: String, query: String): Flow<List<Game>>

    @Query("SELECT DISTINCT category FROM games WHERE platform = :platform ORDER BY category ASC")
    fun getCategories(platform: String): Flow<List<String>>

    @Query("SELECT * FROM games WHERE platform = :platform AND category = :category ORDER BY name ASC")
    fun getGamesByCategory(platform: String, category: String): Flow<List<Game>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGames(games: List<Game>)

    @Query("DELETE FROM games WHERE platform = :platform")
    suspend fun deleteGamesByPlatform(platform: String)

    @Query("DELETE FROM games")
    suspend fun deleteAllGames()
}
