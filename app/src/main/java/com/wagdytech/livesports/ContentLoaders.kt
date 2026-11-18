package com.wagdytech.livesports

import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Leagues {
    val ALL = linkedMapOf(
        "الكل" to listOf(4328, 4335, 4331, 4330, 4329, 4480),
        "الدوري الإنجليزي" to listOf(4328),
        "الدوري الإسباني" to listOf(4335),
        "الدوري الإيطالي" to listOf(4331),
        "الدوري الألماني" to listOf(4330),
        "الدوري الفرنسي" to listOf(4329),
        "الأبطال" to listOf(4480)
    )
    const val API = "https://www.thesportsdb.com/api/v1/json/3"
}

object MatchesLoader {

    private val LIVE_STATUS = setOf("1H", "2H", "HT", "ET", "BT", "P", "LIVE")

    suspend fun live(): List<Match> {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val body = Net.getText("${Leagues.API}/eventsday.php?d=$today&s=Soccer") ?: return emptyList()
        return runCatching {
            val arr = JSONObject(body).optJSONArray("events") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val m = Match.fromJson(o, "Soccer")
                if (m.status in LIVE_STATUS) m else null
            }.sortedBy { it.league }
        }.getOrDefault(emptyList())
    }

    suspend fun upcoming(leagueIds: List<Int>): List<Match> {
        val all = ArrayList<Match>()
        for (id in leagueIds) {
            val body = Net.getText("${Leagues.API}/eventsnextleague.php?id=$id") ?: continue
            runCatching {
                val arr = JSONObject(body).optJSONArray("events") ?: return@runCatching
                for (i in 0 until arr.length()) {
                    all.add(Match.fromJson(arr.getJSONObject(i), "مباريات قادمة"))
                }
            }
        }
        return all.sortByDate()
    }

    suspend fun results(leagueIds: List<Int>): List<Match> {
        val all = ArrayList<Match>()
        for (id in leagueIds) {
            val body = Net.getText("${Leagues.API}/eventspastleague.php?id=$id") ?: continue
            runCatching {
                val arr = JSONObject(body).optJSONArray("events") ?: return@runCatching
                for (i in 0 until arr.length()) {
                    all.add(Match.fromJson(arr.getJSONObject(i), "نتائج"))
                }
            }
        }
        return all.sortByDateDescending()
    }

    private fun MutableList<Match>.sortByDate(): List<Match> =
        sortedWith(compareBy({ it.date ?: "" }, { it.time ?: "" }))

    private fun MutableList<Match>.sortByDateDescending(): List<Match> =
        sortedWith(compareByDescending<Match> { it.date ?: "" }.thenByDescending { it.time ?: "" })
}

object NewsLoader {
    const val FEED = "https://feeds.bbci.co.uk/sport/football/rss.xml"

    suspend fun load(): List<NewsItem> {
        val xml = Net.getText(FEED) ?: return emptyList()
        return runCatching {
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(StringReader(xml))
            val items = ArrayList<NewsItem>()
            var inItem = false
            var title = ""; var link = ""; var date = ""; var desc = ""
            var text = StringBuilder()
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "item" -> { inItem = true; title=""; link=""; date=""; desc=""; text = StringBuilder() }
                        "title", "link", "pubDate", "description" -> text = StringBuilder()
                    }
                    XmlPullParser.TEXT -> if (inItem) text.append(parser.text)
                    XmlPullParser.END_TAG -> when (parser.name) {
                        "title" -> if (inItem) title = text.toString().trim()
                        "link" -> if (inItem) link = text.toString().trim()
                        "pubDate" -> if (inItem) date = text.toString().trim()
                        "description" -> if (inItem) desc = text.toString().trim()
                        "item" -> {
                            if (title.isNotEmpty() && link.isNotEmpty()) {
                                items.add(NewsItem(title, link, date.take(16), desc.take(140)))
                            }
                            inItem = false
                        }
                    }
                }
                event = parser.next()
            }
            items
        }.getOrDefault(emptyList())
    }
}
