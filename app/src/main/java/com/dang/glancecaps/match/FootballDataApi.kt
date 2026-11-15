package com.dang.glancecaps.match

import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate

object FootballDataApi {
    private const val BASE = "https://api.football-data.org/v4"

    private fun headers(key: String) = mapOf("X-Auth-Token" to key)

    fun fetchDay(day: LocalDate, key: String): List<MatchInfo>? {
        val url = "$BASE/matches?dateFrom=${day.minusDays(1)}&dateTo=${day.plusDays(1)}"
        val body = MatchApi.getWith(url, headers(key)) ?: return null
        return try {
            val arr = JSONObject(body).optJSONArray("matches") ?: return emptyList()
            val out = ArrayList<MatchInfo>()
            for (i in 0 until arr.length()) {
                parse(arr.optJSONObject(i) ?: continue)?.let { out.add(it) }
            }
            MatchApi.localDay(day, out)
        } catch (e: Throwable) {
            null
        }
    }

    fun fetchOne(id: String, key: String): MatchInfo? {
        val body = MatchApi.getWith("$BASE/matches/$id", headers(key)) ?: return null
        return try {
            parse(JSONObject(body))
        } catch (e: Throwable) {
            null
        }
    }

    private fun parse(m: JSONObject): MatchInfo? {
        val id = m.optLong("id")
        if (id == 0L) return null
        val kickoff = try {
            Instant.parse(m.optString("utcDate")).toEpochMilli()
        } catch (e: Throwable) {
            0L
        }
        if (kickoff <= 0L) return null
        val status = m.optString("status")
        val state = when (status) {
            "IN_PLAY", "PAUSED", "LIVE", "EXTRA_TIME", "PENALTY_SHOOTOUT" -> "in"
            "FINISHED", "AWARDED", "CANCELLED", "POSTPONED", "SUSPENDED" -> "post"
            else -> "pre"
        }
        val minute = m.optInt("minute", 0)
        val detail = when {
            status == "PAUSED" -> "HT"
            state == "in" -> if (minute > 0) "$minute'" else "LIVE"
            state == "post" -> if (status == "FINISHED") "FT" else status.lowercase().replaceFirstChar { it.uppercase() }
            else -> ""
        }
        val full = m.optJSONObject("score")?.optJSONObject("fullTime")
        fun score(side: String): Int? =
            if (state == "pre" || full == null || full.isNull(side)) null else full.optInt(side)
        val home = team(m.optJSONObject("homeTeam"), score("home")) ?: return null
        val away = team(m.optJSONObject("awayTeam"), score("away")) ?: return null
        val comp = m.optJSONObject("competition")
        return MatchInfo(
            id = "f:$id",
            leagueKey = "f" + (comp?.optLong("id") ?: 0L),
            leagueName = comp?.optString("name").orEmpty(),
            country = m.optJSONObject("area")?.optString("name").orEmpty(),
            kickoff = kickoff,
            state = state,
            detail = detail,
            minute = minute,
            home = home,
            away = away
        )
    }

    private fun team(t: JSONObject?, score: Int?): MatchTeam? {
        if (t == null) return null
        val name = t.optString("shortName").ifBlank { t.optString("name") }
        if (name.isBlank()) return null
        return MatchTeam(name = name, abbr = t.optString("tla"), logo = t.optString("crest"), score = score)
    }
}
