package com.dang.glancecaps.match

import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.Callable
import java.util.concurrent.Executors

object EspnApi {
    private const val BASE = "https://site.api.espn.com/apis/site/v2/sports/soccer"

    private class League(val slug: String, val country: String, val name: String)

    private val leagues = listOf(
        League("eng.1", "England", "Premier League"),
        League("esp.1", "Spain", "LaLiga"),
        League("ita.1", "Italy", "Serie A"),
        League("ger.1", "Germany", "Bundesliga"),
        League("fra.1", "France", "Ligue 1"),
        League("uefa.champions", "", "UEFA Champions League"),
        League("uefa.europa", "", "UEFA Europa League"),
        League("uefa.europa.conf", "", "UEFA Conference League"),
        League("fifa.world", "", "FIFA World Cup"),
        League("fifa.worldq.uefa", "", "World Cup Qualification UEFA"),
        League("uefa.euro", "", "UEFA Euro"),
        League("uefa.nations", "", "UEFA Nations League"),
        League("conmebol.america", "", "Copa America"),
        League("eng.fa", "England", "FA Cup"),
        League("eng.league_cup", "England", "EFL Cup"),
        League("esp.copa_del_rey", "Spain", "Copa del Rey"),
        League("ita.coppa_italia", "Italy", "Coppa Italia"),
        League("ger.dfb_pokal", "Germany", "DFB-Pokal"),
        League("fra.coupe_de_france", "France", "Coupe de France"),
        League("eng.2", "England", "Championship"),
        League("ned.1", "Netherlands", "Eredivisie"),
        League("por.1", "Portugal", "Primeira Liga"),
        League("sau.1", "Saudi Arabia", "Saudi Pro League"),
        League("usa.1", "", "MLS"),
        League("conmebol.libertadores", "", "Copa Libertadores"),
        League("fifa.friendly", "", "International Friendly")
    )

    private val basic: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE

    private fun range(center: LocalDate): String =
        basic.format(center.minusDays(1)) + "-" + basic.format(center.plusDays(1))

    private fun scoreboard(slug: String, center: LocalDate): String? =
        MatchApi.get("$BASE/$slug/scoreboard?dates=${range(center)}&limit=200")
            ?: MatchApi.get("$BASE/$slug/scoreboard?dates=${basic.format(center)}&limit=200")
            ?: if (center == LocalDate.now()) MatchApi.get("$BASE/$slug/scoreboard") else null

    fun fetchDay(day: LocalDate): List<MatchInfo>? {
        val pool = Executors.newFixedThreadPool(8)
        val all = ArrayList<MatchInfo>()
        var ok = false
        try {
            val futures = leagues.map { l -> pool.submit(Callable<List<MatchInfo>?> { fetchLeague(l, day) }) }
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
        return MatchApi.localDay(day, all)
    }

    fun fetchOne(id: String): MatchInfo? {
        val parts = id.split(":", limit = 3)
        if (parts.size < 3) return null
        val slug = parts[0]
        val date = parts[1]
        val eventId = parts[2]
        val league = leagues.firstOrNull { it.slug == slug } ?: League(slug, "", "")
        val center = try {
            LocalDate.parse(date, basic)
        } catch (e: Throwable) {
            return null
        }
        val body = scoreboard(slug, center) ?: return null
        return try {
            val events = JSONObject(body).optJSONArray("events") ?: return null
            for (i in 0 until events.length()) {
                val e = events.optJSONObject(i) ?: continue
                if (e.optString("id") == eventId) return parse(e, league)
            }
            null
        } catch (e: Throwable) {
            null
        }
    }

    private fun fetchLeague(l: League, center: LocalDate): List<MatchInfo>? {
        val body = scoreboard(l.slug, center) ?: return null
        return try {
            val events = JSONObject(body).optJSONArray("events") ?: return emptyList()
            val out = ArrayList<MatchInfo>()
            for (i in 0 until events.length()) {
                val e = events.optJSONObject(i) ?: continue
                parse(e, l)?.let { out.add(it) }
            }
            out
        } catch (e: Throwable) {
            null
        }
    }

    private fun parse(e: JSONObject, l: League): MatchInfo? {
        val eventId = e.optString("id")
        if (eventId.isBlank()) return null
        val comp = e.optJSONArray("competitions")?.optJSONObject(0) ?: return null
        val kickoff = try {
            OffsetDateTime.parse(e.optString("date")).toInstant().toEpochMilli()
        } catch (t: Throwable) {
            0L
        }
        if (kickoff == 0L) return null
        val status = e.optJSONObject("status") ?: comp.optJSONObject("status")
        val type = status?.optJSONObject("type")
        val state = when (type?.optString("state")) {
            "in" -> "in"
            "post" -> "post"
            else -> "pre"
        }
        val clock = status?.optString("displayClock").orEmpty()
        val shortDetail = type?.optString("shortDetail").orEmpty()
        val minute = Regex("\\d+").find(clock)?.value?.toIntOrNull() ?: 0
        val detail = when (state) {
            "in" -> shortDetail.ifBlank { clock }
            "post" -> if (type?.optBoolean("completed") == true) "FT" else type?.optString("description").orEmpty()
            else -> ""
        }
        val competitors = comp.optJSONArray("competitors") ?: return null
        var home: JSONObject? = null
        var away: JSONObject? = null
        for (i in 0 until competitors.length()) {
            val c = competitors.optJSONObject(i) ?: continue
            when (c.optString("homeAway")) {
                "home" -> home = c
                "away" -> away = c
            }
        }
        val h = team(home, state) ?: return null
        val a = team(away, state) ?: return null
        val dateKey = basic.format(Instant.ofEpochMilli(kickoff).atZone(ZoneOffset.UTC))
        return MatchInfo(
            id = "e:${l.slug}:$dateKey:$eventId",
            leagueKey = "e:${l.slug}",
            leagueName = l.name,
            country = l.country,
            kickoff = kickoff,
            state = state,
            detail = detail,
            minute = minute,
            home = h,
            away = a
        )
    }

    private fun team(c: JSONObject?, state: String): MatchTeam? {
        if (c == null) return null
        val t = c.optJSONObject("team") ?: return null
        val name = t.optString("displayName").ifBlank { t.optString("name") }
        if (name.isBlank()) return null
        return MatchTeam(
            name = name,
            abbr = t.optString("abbreviation"),
            logo = t.optString("logo"),
            score = if (state == "pre") null else c.optString("score").toIntOrNull()
        )
    }
}
