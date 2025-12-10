package com.gamestore.app.util

import com.gamestore.app.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URL

object ExternalDownloadFetcher {

    suspend fun fetchExternalDownloads(
        gameName: String,
        platform: Platform,
        customSources: Set<String> = emptySet()
    ): List<ExternalDownloadMatch> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ExternalDownloadMatch>()
        
        platform.extendedDownloads?.let { extendedDownloads ->
            if (!extendedDownloads.enabled) return@withContext results
            
            for (source in extendedDownloads.sources) {
                try {
                    val externalSource = when (source.type) {
                        DownloadSourceType.JSON_URL -> fetchFromJsonUrl(source.path)
                        DownloadSourceType.LOCAL_JSON -> fetchFromLocalJson(source.path)
                        DownloadSourceType.DIRECT_URLS -> null
                    }
                    
                    externalSource?.let { extSource ->
                        val matches = findMatchingDownloads(gameName, extSource)
                        results.addAll(matches.map { match ->
                            ExternalDownloadMatch(
                                sourceName = source.name,
                                download = match
                            )
                        })
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        
        for (customUrl in customSources) {
            try {
                val externalSource = fetchFromJsonUrl(customUrl)
                externalSource?.let { extSource ->
                    val matches = findMatchingDownloads(gameName, extSource)
                    results.addAll(matches.map { match ->
                        ExternalDownloadMatch(
                            sourceName = "${extSource.name} (Custom)",
                            download = match
                        )
                    })
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        return@withContext results
    }

    private fun fetchFromJsonUrl(url: String): ExternalDownloadSource? {
        return try {
            val connection = URL(url).openConnection()
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            val jsonText = connection.getInputStream().bufferedReader().use { it.readText() }
            parseExternalJson(jsonText)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun fetchFromLocalJson(path: String): ExternalDownloadSource? {
        return try {
            val file = File(path)
            if (!file.exists()) return null
            val jsonText = file.readText()
            parseExternalJson(jsonText)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun parseExternalJson(jsonText: String): ExternalDownloadSource? {
        return try {
            val jsonObject = JSONObject(jsonText)
            val name = jsonObject.optString("name", "Unknown Source")
            val downloadsArray = jsonObject.getJSONArray("downloads")
            
            val downloads = mutableListOf<ExternalDownload>()
            for (i in 0 until downloadsArray.length()) {
                val downloadObj = downloadsArray.getJSONObject(i)
                val title = downloadObj.getString("title")
                val uploadDate = downloadObj.optString("uploadDate", null)
                val fileSize = downloadObj.optString("fileSize", null)
                
                val urisArray = downloadObj.getJSONArray("uris")
                val uris = mutableListOf<String>()
                for (j in 0 until urisArray.length()) {
                    uris.add(urisArray.getString(j))
                }
                
                downloads.add(
                    ExternalDownload(
                        title = title,
                        uploadDate = uploadDate,
                        fileSize = fileSize,
                        uris = uris
                    )
                )
            }
            
            ExternalDownloadSource(name = name, downloads = downloads)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun findMatchingDownloads(
        gameName: String,
        source: ExternalDownloadSource
    ): List<ExternalDownload> {
        val normalizedGameName = gameName.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .trim()
        
        return source.downloads.filter { download ->
            val normalizedTitle = download.title.lowercase()
                .replace(Regex("[^a-z0-9\\s]"), "")
                .trim()
            
            val gameWords = normalizedGameName.split("\\s+".toRegex())
            val titleWords = normalizedTitle.split("\\s+".toRegex())
            
            val matchCount = gameWords.count { gameWord ->
                titleWords.any { titleWord ->
                    titleWord.contains(gameWord) || gameWord.contains(titleWord)
                }
            }
            
            matchCount >= (gameWords.size * 0.6).toInt().coerceAtLeast(1)
        }.sortedByDescending { download ->
            download.uploadDate ?: ""
        }
    }
}

data class ExternalDownloadMatch(
    val sourceName: String,
    val download: ExternalDownload
)
