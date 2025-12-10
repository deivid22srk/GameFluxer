package com.gamestore.app.data.model

data class ExternalDownloadSource(
    val name: String,
    val downloads: List<ExternalDownload>
)

data class ExternalDownload(
    val title: String,
    val uploadDate: String?,
    val fileSize: String?,
    val uris: List<String>
)
