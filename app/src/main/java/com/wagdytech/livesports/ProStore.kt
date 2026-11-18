package com.wagdytech.livesports

import android.content.Context

class ProStore(context: Context) {
    private val prefs = context.getSharedPreferences("pro", Context.MODE_PRIVATE)

    fun isActive(): Boolean = prefs.getBoolean("active", false)

    fun activate(code: String) {
        prefs.edit().putBoolean("active", true).putString("code", code).apply()
    }
}
