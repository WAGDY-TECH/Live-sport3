package com.wagdytech.livesports

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AppConfig(
    val appName: String? = null,
    val accentColor: String? = null,
    val backgroundColor: String? = null,
    val playlistUrl: String? = null,
    val announcementTitle: String? = null,
    val announcementMessage: String? = null,
    val latestVersionCode: Int = 0,
    val updateMessage: String? = null,
    val updateApkUrl: String? = null,
    val forceUpdate: Boolean = false,
    val proContactUrl: String? = null,
    val proPrice: String? = null
) {
    val hasAnnouncement get() = !announcementTitle.isNullOrBlank() || !announcementMessage.isNullOrBlank()
    val hasUpdate get() = latestVersionCode > 0 && !updateApkUrl.isNullOrBlank()
}

object RemoteConfig {

    private const val PREFS = "remote_config"
    private const val KEY_CACHED = "cached_json"

    fun fetchUrl(): String = BuildConfig.REMOTE_CONFIG_URL

    fun loadCached(context: Context): AppConfig? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_CACHED, null)?.let { parse(it) }

    suspend fun fetch(context: Context): AppConfig? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(fetchUrl()).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("Cache-Control", "no-cache")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_CACHED, body).apply()
            parse(body)
        }.getOrNull()
    }

    private fun parse(json: String): AppConfig {
        val root = JSONObject(json)
        return AppConfig(
            appName = root.optStringOrNull("appName"),
            accentColor = root.optStringOrNull("accentColor"),
            backgroundColor = root.optStringOrNull("backgroundColor"),
            playlistUrl = root.optStringOrNull("playlistUrl"),
            announcementTitle = root.optStringOrNull("announcementTitle"),
            announcementMessage = root.optStringOrNull("announcementMessage"),
            latestVersionCode = root.optInt("latestVersionCode", 0),
            updateMessage = root.optStringOrNull("updateMessage"),
            updateApkUrl = root.optStringOrNull("updateApkUrl"),
            forceUpdate = root.optBoolean("forceUpdate", false),
            proContactUrl = root.optStringOrNull("proContactUrl"),
            proPrice = root.optStringOrNull("proPrice")
        )
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        val v = optString(key, "").trim()
        return v.ifEmpty { null }
    }
}
