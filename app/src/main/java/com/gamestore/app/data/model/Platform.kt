package com.gamestore.app.data.model

data class Platform(
    val name: String,
    val databasePath: String,
    val databases: List<DatabaseEntry> = emptyList(),
    val extendedDownloads: ExtendedDownloads? = null
)

data class DatabaseEntry(
    val name: String,
    val path: String
)

data class ExtendedDownloads(
    val enabled: Boolean = false,
    val sources: List<DownloadSource> = emptyList()
)

data class DownloadSource(
    val name: String,
    val type: DownloadSourceType,
    val path: String
)

enum class DownloadSourceType {
    JSON_URL,
    LOCAL_JSON,
    DIRECT_URLS
}