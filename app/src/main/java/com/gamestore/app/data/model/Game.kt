package com.gamestore.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "games")
data class Game(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val size: String,
    val rating: Float,
    val developer: String,
    val category: String,
    val platform: String,
    val iconUrl: String,
    val bannerUrl: String,
    val screenshots: String,
    val downloadUrl: String,
    val releaseDate: String
)
