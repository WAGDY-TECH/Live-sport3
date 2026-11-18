package com.wagdytech.livesports

import org.json.JSONObject

data class Match(
    val league: String,
    val home: String,
    val away: String,
    val homeScore: Int?,
    val awayScore: Int?,
    val date: String?,
    val time: String?,
    val status: String?
) {
    val isLive: Boolean
        get() = status in setOf("1H", "2H", "HT", "ET", "BT", "P", "LIVE")

    companion object {
        fun fromJson(o: JSONObject, leagueFallback: String): Match = Match(
            league = o.optString("strLeague", leagueFallback),
            home = o.optString("strHomeTeam", ""),
            away = o.optString("strAwayTeam", ""),
            homeScore = if (o.has("intHomeScore") && !o.isNull("intHomeScore")) o.optInt("intHomeScore") else null,
            awayScore = if (o.has("intAwayScore") && !o.isNull("intAwayScore")) o.optInt("intAwayScore") else null,
            date = o.optString("dateEvent", null),
            time = o.optString("strTime", null),
            status = o.optString("strStatus", "").ifEmpty { null }
        )
    }
}

data class NewsItem(
    val title: String,
    val link: String,
    val date: String?,
    val summary: String?
)
