package com.wagdytech.livesports

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object Net {
    suspend fun getText(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 12000
            conn.readTimeout = 12000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) LiveSportsApp/1.3")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            body
        }.getOrNull()
    }
}
