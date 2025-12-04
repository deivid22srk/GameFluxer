package com.gamestore.app.data.local

import androidx.room.TypeConverter
import com.gamestore.app.data.model.DownloadStatus

class Converters {
    @TypeConverter
    fun fromDownloadStatus(status: DownloadStatus): String {
        return status.name
    }

    @TypeConverter
    fun toDownloadStatus(value: String): DownloadStatus {
        return try {
            DownloadStatus.valueOf(value)
        } catch (e: IllegalArgumentException) {
            DownloadStatus.QUEUED
        }
    }
}
