package com.skydronex.fr24fortified.data.map

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class RainViewerRepository {

    private data class RainFrame(
        val host: String,
        val path: String
    )

    suspend fun latestRadarTileTemplate(): String? = withContext(Dispatchers.IO) {
        val conn = URL("https://api.rainviewer.com/public/weather-maps.json")
            .openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            conn.requestMethod = "GET"
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return@withContext null

            val root = JSONObject(conn.inputStream.bufferedReader().readText())
            val host = root.optString("host").ifBlank { "https://tilecache.rainviewer.com" }
            val radar = root.optJSONObject("radar") ?: return@withContext null

            val past = radar.optJSONArray("past")
            val nowcast = radar.optJSONArray("nowcast")

            val frame = latestFrame(host, nowcast) ?: latestFrame(host, past) ?: return@withContext null
            "${frame.host}${frame.path}/256/{z}/{x}/{y}/2/1_1.png"
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun latestFrame(host: String, frames: org.json.JSONArray?): RainFrame? {
        if (frames == null || frames.length() == 0) return null
        val frame = frames.optJSONObject(frames.length() - 1) ?: return null
        val path = frame.optString("path").takeIf { it.isNotBlank() } ?: return null
        return RainFrame(host = host, path = path)
    }
}
