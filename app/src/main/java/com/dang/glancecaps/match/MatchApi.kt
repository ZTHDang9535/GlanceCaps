package com.dang.glancecaps.match

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.LruCache
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class MatchTeam(val name: String, val abbr: String, val logo: String, val score: Int?) {
    val short: String get() = abbr.ifBlank { name }
}

class MatchInfo(
    val id: String,
    val leagueKey: String,
    val leagueName: String,
    val country: String,
    val kickoff: Long,
    val state: String,
    val detail: String,
    val minute: Int,
    val home: MatchTeam,
    val away: MatchTeam,
) {
    val live: Boolean get() = state == "in"
    val finished: Boolean get() = state == "post"
    val homeScore: String get() = home.score?.toString() ?: "-"
    val awayScore: String get() = away.score?.toString() ?: "-"
    val scoreText: String get() = if (state == "pre") "vs" else "$homeScore - $awayScore"
    val competition: String get() = if (country.isBlank()) leagueName else "$country: $leagueName"

    fun withMeta(league: String, start: Long): MatchInfo = MatchInfo(
        id, leagueKey, leagueName.ifBlank { league }, country,
        if (kickoff > 0L) kickoff else start,
        state, detail, minute, home, away
    )
}

class Competition(val key: String, val name: String, val country: String) {
    val label: String get() = if (country.isBlank()) name else "$country: $name"
}

object MatchSources {
    const val ESPN = "espn"
    const val APIFOOTBALL = "apifootball"
    const val THESPORTSDB = "thesportsdb"
    const val SOFASCORE = "sofascore"

    val all: List<Pair<String, String>> = listOf(
        APIFOOTBALL to "API-Football",
        ESPN to "ESPN",
        THESPORTSDB to "TheSportsDB",
        SOFASCORE to "SofaScore"
    )

    fun needsKey(key: String): Boolean = key == APIFOOTBALL
}

object MatchApi {
    private const val BASE = "https://prod-public-api.livescore.com/v1/api/app"
    private const val IMAGES = "https://lsm-static-prod.livescore.com/high/"
    fun get(url: String): String? = getWith(url, emptyMap())

    fun getWith(url: String, headers: Map<String, String>): String? {
        var c: HttpURLConnection? = null
        return try {
            c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.setRequestProperty("User-Agent", "Mozilla/5.0")
            c.setRequestProperty("Accept", "application/json")
            for ((k, v) in headers) c.setRequestProperty(k, v)
            if (c.responseCode != 200) null else c.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Throwable) {
            null
        } finally {
            c?.disconnect()
        }
    }

    fun collectLocal(day: LocalDate, loader: (LocalDate) -> List<MatchInfo>?): List<MatchInfo>? {
        val zone = ZoneId.systemDefault()
        val days = listOf(day.minusDays(1), day, day.plusDays(1))
        val pool = Executors.newFixedThreadPool(3)
        val all = ArrayList<MatchInfo>()
        var ok = false
        try {
            val futures = days.map { d -> pool.submit(Callable<List<MatchInfo>?> { loader(d) }) }
            for (f in futures) {
                val r = try {
                    f.get()
                } catch (e: Throwable) {
                    null
                }
                if (r != null) {
                    ok = true
                    all.addAll(r)
                }
            }
        } finally {
            pool.shutdown()
        }
        if (!ok) return null
        return localDay(day, all)
    }

    fun localDay(day: LocalDate, all: List<MatchInfo>): List<MatchInfo> {
        val zone = ZoneId.systemDefault()
        return all.distinctBy { it.id }
            .filter { it.kickoff > 0L && Instant.ofEpochMilli(it.kickoff).atZone(zone).toLocalDate() == day }
            .sortedWith(compareBy<MatchInfo> {
                when (it.state) {
                    "in" -> 0
                    "pre" -> 1
                    else -> 2
                }
            }.thenBy { it.kickoff })
    }

    fun fetchLocalDay(day: LocalDate, source: String, apiKey: String): List<MatchInfo>? = when (source) {
        MatchSources.SOFASCORE -> collectLocal(day) { fetchSofaUtcDay(it) }?.filter { MatchLeagues.allowed(it) }
        MatchSources.APIFOOTBALL -> ApiFootballApi.fetchDay(day, apiKey)
        MatchSources.THESPORTSDB -> TheSportsDbApi.fetchDay(day)
        else -> EspnApi.fetchDay(day)
    }

    private fun sofaGet(url: String): String? {
        var c: HttpURLConnection? = null
        return try {
            c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10000
            c.readTimeout = 15000
            c.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
            )
            c.setRequestProperty("Origin", "https://www.sofascore.com")
            c.setRequestProperty("Referer", "https://www.sofascore.com/")
            c.setRequestProperty("Accept", "*/*")
            c.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            if (c.responseCode != 200) null else c.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Throwable) {
            null
        } finally {
            c?.disconnect()
        }
    }

    private fun fetchSofaUtcDay(day: LocalDate): List<MatchInfo>? {
        val body = sofaGet("https://api.sofascore.com/api/v1/sport/football/scheduled-events/$day") ?: return null
        return try {
            val events = JSONObject(body).optJSONArray("events") ?: return emptyList()
            val out = ArrayList<MatchInfo>()
            for (i in 0 until events.length()) {
                parseSofa(events.optJSONObject(i) ?: continue)?.let { out.add(it) }
            }
            out
        } catch (e: Throwable) {
            null
        }
    }

    private fun fetchSofaOne(id: String): MatchInfo? {
        val body = sofaGet("https://api.sofascore.com/api/v1/event/$id") ?: return null
        return try {
            parseSofa(JSONObject(body).optJSONObject("event") ?: return null)
        } catch (e: Throwable) {
            null
        }
    }

    private fun sofaTeam(t: JSONObject?, score: JSONObject?): MatchTeam? {
        if (t == null) return null
        val id = t.optLong("id")
        return MatchTeam(
            name = t.optString("name"),
            abbr = t.optString("nameCode"),
            logo = if (id > 0L) "https://api.sofascore.app/api/v1/team/$id/image" else "",
            score = if (score != null && score.has("current")) score.optInt("current") else null
        )
    }

    private fun parseSofa(e: JSONObject): MatchInfo? {
        val id = e.optLong("id")
        if (id == 0L) return null
        val tournament = e.optJSONObject("tournament")
        val unique = tournament?.optJSONObject("uniqueTournament")
        val category = tournament?.optJSONObject("category")
        val key = (unique?.optLong("id") ?: tournament?.optLong("id") ?: 0L).toString()
        val league = unique?.optString("name").orEmpty().ifBlank { tournament?.optString("name").orEmpty() }
        val status = e.optJSONObject("status")
        val code = status?.optInt("code") ?: 0
        val state = when (status?.optString("type")) {
            "inprogress", "interrupted" -> "in"
            "finished", "postponed", "canceled", "cancelled" -> "post"
            else -> "pre"
        }
        var minute = 0
        var detail = ""
        if (state == "in") {
            val start = e.optJSONObject("time")?.optLong("currentPeriodStartTimestamp") ?: 0L
            val base = when (code) {
                6 -> 0
                7 -> 45
                41 -> 90
                42 -> 105
                else -> 0
            }
            minute = if (start > 0L) base + ((System.currentTimeMillis() / 1000L - start) / 60L).toInt() + 1 else base
            detail = if (code == 31) "HT" else "$minute'"
        } else if (state == "post") {
            detail = if (status?.optString("type") == "finished") "FT" else status?.optString("description").orEmpty()
        }
        val home = sofaTeam(e.optJSONObject("homeTeam"), e.optJSONObject("homeScore")) ?: return null
        val away = sofaTeam(e.optJSONObject("awayTeam"), e.optJSONObject("awayScore")) ?: return null
        return MatchInfo(
            id = "s:$id",
            leagueKey = "s$key",
            leagueName = league,
            country = category?.optString("name").orEmpty(),
            kickoff = e.optLong("startTimestamp") * 1000L,
            state = state,
            detail = detail,
            minute = minute,
            home = home,
            away = away
        )
    }

    fun competitionsOf(matches: List<MatchInfo>): List<Competition> {
        val seen = LinkedHashMap<String, Competition>()
        for (m in matches) {
            if (m.leagueKey.isNotBlank() && !seen.containsKey(m.leagueKey)) {
                seen[m.leagueKey] = Competition(m.leagueKey, m.leagueName, m.country)
            }
        }
        return seen.values.sortedWith(
            compareBy<Competition> { MatchLeagues.rank(it.country, it.name) }.thenBy { it.country }.thenBy { it.name }
        )
    }

    private fun fetchUtcDay(day: LocalDate): List<MatchInfo>? {
        val country = Locale.getDefault().country.ifBlank { "US" }
        val url = BASE + "/date/soccer/" + day.format(DateTimeFormatter.BASIC_ISO_DATE) + "/0?countryCode=" + country + "&locale=en"
        val body = get(url) ?: return null
        return try {
            val stages = JSONObject(body).optJSONArray("Stages") ?: return emptyList()
            val out = ArrayList<MatchInfo>()
            for (i in 0 until stages.length()) {
                val stage = stages.optJSONObject(i) ?: continue
                val events = stage.optJSONArray("Events") ?: continue
                val key = stage.optString("Sid")
                val name = stage.optString("Snm")
                val cnm = stage.optString("Cnm")
                for (k in 0 until events.length()) {
                    parseEvent(events.optJSONObject(k) ?: continue, key, name, cnm)?.let { out.add(it) }
                }
            }
            out
        } catch (e: Throwable) {
            null
        }
    }

    fun fetchOne(id: String, apiKey: String): MatchInfo? = when {
        id.startsWith("e:") -> EspnApi.fetchOne(id.removePrefix("e:"))
        id.startsWith("a:") -> ApiFootballApi.fetchOne(id.removePrefix("a:"), apiKey)
        id.startsWith("t:") -> TheSportsDbApi.fetchOne(id.removePrefix("t:"))
        id.startsWith("s:") -> fetchSofaOne(id.removePrefix("s:"))
        else -> fetchLivescoreOne(id.removePrefix("l:"))
    }

    private fun fetchLivescoreOne(id: String): MatchInfo? {
        val body = get("$BASE/scoreboard/soccer/$id?locale=en") ?: return null
        return try {
            val root = JSONObject(body)
            val stg = root.optJSONObject("Stg")
            parseEvent(
                root,
                stg?.optString("Sid").orEmpty(),
                stg?.optString("Nm").orEmpty().ifBlank { root.optString("Snm") },
                stg?.optString("Cnm").orEmpty().ifBlank { root.optString("Cnm") }
            )
        } catch (e: Throwable) {
            null
        }
    }

    private fun stateOf(eps: String): String {
        val s = eps.trim()
        val upper = s.uppercase(Locale.US)
        return when {
            s.isEmpty() || upper == "NS" || upper == "-" -> "pre"
            s.contains("'") || upper == "HT" || upper == "ET" || upper == "BT" || upper == "PEN" -> "in"
            upper.startsWith("FT") || upper.startsWith("AET") || upper == "AP" || upper.startsWith("POSTP") ||
                upper.startsWith("CANC") || upper.startsWith("ABD") || upper.startsWith("AWARD") || upper.startsWith("WO") -> "post"
            else -> "pre"
        }
    }

    private fun parseEsd(raw: String): Long {
        if (raw.length < 14) return 0L
        return try {
            val sdf = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            sdf.parse(raw.substring(0, 14))?.time ?: 0L
        } catch (e: Throwable) {
            0L
        }
    }

    private fun badge(img: String): String = when {
        img.isBlank() -> ""
        img.startsWith("http") -> img
        img.contains("/") -> IMAGES + img
        else -> IMAGES + "enet/" + img + ".png"
    }

    private fun team(arr: org.json.JSONArray?, score: String?): MatchTeam? {
        val t = arr?.optJSONObject(0) ?: return null
        return MatchTeam(
            name = t.optString("Nm"),
            abbr = t.optString("Abr"),
            logo = badge(t.optString("Img")),
            score = score?.trim()?.toIntOrNull()
        )
    }

    private fun parseEvent(ev: JSONObject, key: String, league: String, country: String): MatchInfo? {
        val id = ev.optString("Eid")
        if (id.isBlank()) return null
        val eps = ev.optString("Eps")
        val state = stateOf(eps)
        val home = team(ev.optJSONArray("T1"), if (ev.has("Tr1")) ev.opt("Tr1")?.toString() else null) ?: return null
        val away = team(ev.optJSONArray("T2"), if (ev.has("Tr2")) ev.opt("Tr2")?.toString() else null) ?: return null
        val minute = Regex("\\d+").find(eps)?.value?.toIntOrNull() ?: 0
        return MatchInfo(
            id = "l:$id",
            leagueKey = key,
            leagueName = league,
            country = country,
            kickoff = parseEsd(ev.opt("Esd")?.toString().orEmpty()),
            state = state,
            detail = eps.trim(),
            minute = minute,
            home = home,
            away = away
        )
    }
}

object MatchLogos {
    private val cache = LruCache<String, Bitmap>(32)

    fun load(url: String): Bitmap? {
        if (url.isBlank()) return null
        cache.get(url)?.let { return it }
        var c: HttpURLConnection? = null
        return try {
            c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.setRequestProperty("User-Agent", "Mozilla/5.0")
            val raw = c.inputStream.use { BitmapFactory.decodeStream(it) } ?: return null
            val fitted = fit(raw, 128)
            cache.put(url, fitted)
            fitted
        } catch (e: Throwable) {
            null
        } finally {
            c?.disconnect()
        }
    }

    private fun fit(src: Bitmap, size: Int): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val scale = minOf(size.toFloat() / src.width, size.toFloat() / src.height)
        val w = src.width * scale
        val h = src.height * scale
        val dst = RectF((size - w) / 2f, (size - h) / 2f, (size + w) / 2f, (size + h) / 2f)
        Canvas(out).drawBitmap(src, null, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    fun scoreBitmap(text: String): Bitmap {
        val out = Bitmap.createBitmap(256, 128, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        paint.textSize = 88f
        val y = 64f - (paint.descent() + paint.ascent()) / 2f
        Canvas(out).drawText(text, 128f, y, paint)
        return out
    }

    fun placeholder(): Bitmap {
        val out = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.parseColor("#8E8E93")
        Canvas(out).drawCircle(64f, 64f, 56f, p)
        return out
    }

    fun pair(home: Bitmap?, away: Bitmap?): Bitmap {
        val out = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(home ?: placeholder(), null, RectF(0f, 0f, 120f, 120f), paint)
        canvas.drawBitmap(away ?: placeholder(), null, RectF(72f, 72f, 192f, 192f), paint)
        return out
    }
}
