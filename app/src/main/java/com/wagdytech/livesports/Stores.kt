package com.wagdytech.livesports

import android.content.Context

class FavoritesStore(context: Context) {
    private val prefs = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)

    fun all(): Set<String> = prefs.getStringSet("urls", emptySet()) ?: emptySet()

    fun isFavorite(url: String): Boolean = all().contains(url)

    fun toggle(url: String) {
        val current = all().toMutableSet()
        if (!current.add(url)) current.remove(url)
        prefs.edit().putStringSet("urls", current).apply()
    }
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var playlistUrl: String?
        get() = prefs.getString("playlist_url", null)
        set(value) = prefs.edit().putString("playlist_url", value).apply()
}
