package com.dang.glancecaps.match

import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

object TheSportsDbApi {
    private const val BASE = "https://www.thesportsdb.com/api/v1/json/123"

    fun fetchDay(day: LocalDate): List<MatchInfo>? =
        MatchApi.collectLocal(day) { d -> fetchDate(d) }?.filter { MatchLeagues.allowed(it) }

    private fun fetchDate(day: LocalDate): List<MatchInfo>? {
        val body = MatchApi.get("$BASE/eventsday.php?d=$day&s=Soccer") ?: return null
        return try {
            val events = JSONObject(body).optJSONArray("events") ?: return emptyList()
            val out = ArrayList<MatchInfo>()
            for (i in 0 until events.length()) {
                parse(events.optJSONObject(i) ?: continue)?.let { out.add(it) }
            }
            out
        } catch (e: Throwable) {
            null
        }
    }

    private fun normalize(raw: String): Pair<String, String> {
        val n = raw.lowercase()
        return when {
            n.startsWith("english") && n.contains("premier league") -> "England" to "Premier League"
            n.startsWith("english") && (n.contains("championship")) -> "England" to "Championship"
            n.startsWith("english") && (n.contains("league 1") || n.contains("league one")) -> "England" to "League One"
            n.startsWith("english") && (n.contains("league 2") || n.contains("league two")) -> "England" to "League Two"
            n.startsWith("english") && n.contains("fa cup") -> "England" to "FA Cup"
            n.startsWith("english") && (n.contains("league cup") || n.contains("efl cup")) -> "England" to "EFL Cup"
            n.startsWith("spanish") && n.contains("la liga") -> "Spain" to "LaLiga"
            n.startsWith("italian") && n.contains("serie a") -> "Italy" to "Serie A"
            n.startsWith("german") && n.contains("bundesliga") && !n.contains("2") -> "Germany" to "Bundesliga"
            n.startsWith("vietnam") && n.contains("league 1") -> "Vietnam" to "V.League 1"
            n.startsWith("vietnam") && n.contains("league 2") -> "Vietnam" to "V.League 2"
            n.startsWith("vietnam") && n.contains("cup") -> "Vietnam" to "Vietnam Cup"
            else -> "" to raw
        }
    }

    private fun parse(e: JSONObject): MatchInfo? {
        val id = e.optString("idEvent")
        if (id.isBlank()) return null
        val stamp = e.optString("strTimestamp")
        val kickoff = try {
            LocalDateTime.parse(stamp.replace(" ", "T").substringBefore("+").removeSuffix("Z")).toInstant(ZoneOffset.UTC).toEpochMilli()
        } catch (t: Throwable) {
            0L
        }
        if (kickoff <= 0L) return null
        val status = e.optString("strStatus").trim()
        val low = status.lowercase()
        val state = when {
            low.isEmpty() || low == "ns" || low.contains("not started") || low == "tbd" -> "pre"
            low.contains("finish") || low == "ft" || low == "aet" || low == "pen" || low.contains("postpon") ||
                low.contains("cancel") || low.contains("abandon") -> "post"
            else -> "in"
        }
        val progress = e.optString("strProgress")
        val minute = Regex("\\d+").find(progress)?.value?.toIntOrNull() ?: 0
        val detail = when (state) {
            "in" -> if (minute > 0) "$minute'" else status
            "post" -> if (low.contains("finish") || low == "ft") "FT" else status
            else -> ""
        }
        val (country, name) = normalize(e.optString("strLeague"))
        val home = team(e, "Home", state) ?: return null
        val away = team(e, "Away", state) ?: return null
        return MatchInfo(
            id = "t:$id",
            leagueKey = "t:" + e.optString("idLeague"),
            leagueName = name,
            country = country,
            kickoff = kickoff,
            state = state,
            detail = detail,
            minute = minute,
            home = home,
            away = away
        )
    }

    private fun team(e: JSONObject, side: String, state: String): MatchTeam? {
        val name = e.optString("str${side}Team")
        if (name.isBlank()) return null
        val raw = e.optString("int${side}Score")
        return MatchTeam(
            name = name,
            abbr = name.filter { it.isLetter() }.take(3).uppercase(),
            logo = e.optString("str${side}TeamBadge").takeIf { it != "null" }.orEmpty(),
            score = if (state == "pre") null else raw.toIntOrNull()
        )
    }

    fun fetchOne(id: String): MatchInfo? {
        val body = MatchApi.get("$BASE/lookupevent.php?id=$id") ?: return null
        return try {
            val e = JSONObject(body).optJSONArray("events")?.optJSONObject(0) ?: return null
            parse(e)
        } catch (t: Throwable) {
            null
        }
    }
}
