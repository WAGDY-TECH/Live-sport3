package com.wagdytech.livesports

import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * نقاط نهاية الـ API — كلها قابلة للتغيير من لوحة التحكم عن بُعد
 * عبر الحقول: matchesApiUrl / newsApiUrl / sportsDbKey في app-config.json
 */
object ApiEndpoints {
    /** مجاني بدون مفتاح — مباريات مباشرة (ESPN public API) */
    @Volatile var matchesApiUrl: String = "https://site.api.espn.com/apis/site/v2/sports/soccer"

    /** مجاني — أخبار رياضية RSS */
    @Volatile var newsApiUrl: String = "https://feeds.bbci.co.uk/sport/football/rss.xml"

    /** مصدر احتياطي — TheSportsDB (احصل على مفتاحك المجاني من thesportsdb.com) */
    @Volatile var sportsDbUrl: String = "https://www.thesportsdb.com/api/v1/json"
    @Volatile var sportsDbKey: String = "123"

    fun tsdb(): String = "$sportsDbUrl/$sportsDbKey"
}

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

    /** معرفات TheSportsDB ↔ مسارات ESPN للبث المباشر المجاني */
    val ESPN_SLUGS = linkedMapOf(
        4328 to "eng.1",
        4335 to "esp.1",
        4331 to "ita.1",
        4330 to "ger.1",
        4329 to "fra.1",
        4480 to "uefa.champions"
    )

    val ESPN_NAMES = mapOf(
        "eng.1" to "الدوري الإنجليزي",
        "esp.1" to "الدوري الإسباني",
        "ita.1" to "الدوري الإيطالي",
        "ger.1" to "الدوري الألماني",
        "fra.1" to "الدوري الفرنسي",
        "uefa.champions" to "دوري أبطال أوروبا"
    )
}

object MatchesLoader {

    private val LIVE_STATUS = setOf("1H", "2H", "HT", "ET", "BT", "P", "LIVE")

    suspend fun live(): List<Match> {
        // المصدر الأساسي: ESPN (مجاني بدون مفتاح، حالة مباشر فعلية)
        val espn = runCatching { espnLive() }.getOrDefault(emptyList())
        if (espn.isNotEmpty()) return espn
        // المصدر الاحتياطي: TheSportsDB
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val body = Net.getText("${ApiEndpoints.tsdb()}/eventsday.php?d=$today&s=Soccer") ?: return emptyList()
        return runCatching {
            val arr = JSONObject(body).optJSONArray("events") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val m = Match.fromJson(o, "Soccer")
                if (m.status in LIVE_STATUS) m else null
            }.sortedBy { it.league }
        }.getOrDefault(emptyList())
    }

    private suspend fun espnLive(): List<Match> {
        val out = ArrayList<Match>()
        for ((_, slug) in Leagues.ESPN_SLUGS) {
            val body = Net.getText("${ApiEndpoints.matchesApiUrl}/$slug/scoreboard") ?: continue
            runCatching {
                out.addAll(parseEspnScoreboard(body, onlyLive = true))
            }
        }
        return out.sortedBy { it.league }
    }

    suspend fun upcoming(leagueIds: List<Int>): List<Match> {
        // المصدر الأساسي: TheSportsDB — كل مباريات الفترة القادمة بطلب واحد لكل دوري
        val tsdb = upOrPast(leagueIds, next = true)
        if (tsdb.isNotEmpty()) return tsdb
        // احتياطي: ESPN — اليوم + الأيام القادمة (حتى 3 أيام)
        return espnRange(leagueIds, next = true, days = 3)
    }

    suspend fun results(leagueIds: List<Int>): List<Match> {
        val tsdb = upOrPast(leagueIds, next = false)
        if (tsdb.isNotEmpty()) return tsdb
        return espnRange(leagueIds, next = false, days = 3)
    }

    private suspend fun upOrPast(leagueIds: List<Int>, next: Boolean): List<Match> {
        val all = ArrayList<Match>()
        val endpoint = if (next) "eventsnextleague.php" else "eventspastleague.php"
        for (id in leagueIds) {
            val body = Net.getText("${ApiEndpoints.tsdb()}/$endpoint?id=$id") ?: continue
            runCatching {
                val arr = JSONObject(body).optJSONArray("events") ?: return@runCatching
                val leagueName = Leagues.ALL.entries.firstOrNull { id in it.value }?.key ?: ""
                for (i in 0 until arr.length()) {
                    all.add(Match.fromJson(arr.getJSONObject(i), leagueName))
                }
            }
        }
        return if (next) all.sortByDate() else all.sortByDateDescending()
    }

    private suspend fun espnRange(leagueIds: List<Int>, next: Boolean, days: Int): List<Match> {
        val out = ArrayList<Match>()
        val dates = ArrayList<String>()
        val fmt = SimpleDateFormat("yyyyMMdd", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        if (!next) cal.add(Calendar.DAY_OF_MONTH, -1) // نتائج تبدأ من أمس
        for (i in 0 until days) {
            dates.add(fmt.format(cal.time))
            cal.add(Calendar.DAY_OF_MONTH, if (next) 1 else -1)
        }
        for (d in dates) {
            for (id in leagueIds) {
                val slug = Leagues.ESPN_SLUGS[id] ?: continue
                val body = Net.getText("${ApiEndpoints.matchesApiUrl}/$slug/scoreboard?dates=$d") ?: continue
                runCatching { out.addAll(parseEspnScoreboard(body, onlyLive = false)) }
            }
        }
        return if (next) out.sortByDate() else out.sortByDateDescending()
    }

    private fun parseEspnScoreboard(body: String, onlyLive: Boolean): List<Match> {
        val root = JSONObject(body)
        val arr = root.optJSONArray("events") ?: return emptyList()
        val leagueName = root.optJSONArray("leagues")?.optJSONObject(0)
            ?.optString("name")?.takeIf { it.isNotEmpty() }?.let { enName ->
                // اختصار أسماء الدوريات الطويلة
                when {
                    enName.startsWith("English Premier") -> "الدوري الإنجليزي"
                    enName.startsWith("Spanish") || enName.contains("LaLiga", true) -> "الدوري الإسباني"
                    enName.startsWith("Italian") || enName.contains("Serie", true) -> "الدوري الإيطالي"
                    enName.startsWith("German") || enName.contains("Bundesliga", true) -> "الدوري الألماني"
                    enName.startsWith("French") || enName.contains("Ligue", true) -> "الدوري الفرنسي"
                    enName.contains("UEFA", true) || enName.contains("Champions", true) -> "دوري أبطال أوروبا"
                    else -> enName
                }
            }
        val out = ArrayList<Match>()
        for (i in 0 until arr.length()) {
            val ev = arr.getJSONObject(i)
            val state = ev.optJSONObject("status")?.optJSONObject("type")?.optString("state") ?: continue
            if (onlyLive && state != "in") continue
            val comp = ev.optJSONArray("competitions")?.optJSONObject(0) ?: continue
            var home = ""; var homeScore: Int? = null
            var away = ""; var awayScore: Int? = null
            val competitors = comp.optJSONArray("competitors") ?: continue
            for (j in 0 until competitors.length()) {
                val c = competitors.getJSONObject(j)
                val team = c.optJSONObject("team")?.optString("displayName") ?: continue
                val score = c.optString("score").toIntOrNull()
                if (c.optString("homeAway") == "home") { home = team; homeScore = score }
                else { away = team; awayScore = score }
            }
            if (home.isEmpty() || away.isEmpty()) continue
            // date مثل: 2025-11-08T17:30Z
            val iso = ev.optString("date")
            val statusText = when (state) {
                "in" -> "LIVE"
                "post" -> "FT"
                else -> "NS"
            }
            out.add(
                Match(
                    league = leagueName ?: "",
                    home = home,
                    away = away,
                    homeScore = if (state == "pre") null else homeScore,
                    awayScore = if (state == "pre") null else awayScore,
                    date = iso.take(10).ifEmpty { null },
                    time = if (iso.length >= 16) iso.substring(11, 16) else null,
                    status = statusText
                )
            )
        }
        return out
    }

    private fun MutableList<Match>.sortByDate(): List<Match> =
        sortedWith(compareBy({ it.date ?: "" }, { it.time ?: "" }))

    private fun MutableList<Match>.sortByDateDescending(): List<Match> =
        sortedWith(compareByDescending<Match> { it.date ?: "" }.thenByDescending { it.time ?: "" })
}

object NewsLoader {

    suspend fun load(): List<NewsItem> {
        val feed = ApiEndpoints.newsApiUrl
        val xml = Net.getText(feed) ?: return emptyList()
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
