package com.dang.glancecaps.match

import org.json.JSONObject
import java.net.URLEncoder
import java.time.LocalDate
import java.time.ZoneId

object ApiFootballApi {
    private const val BASE = "https://v3.football.api-sports.io"

    private fun headers(key: String): Map<String, String> = mapOf("x-apisports-key" to key.trim())

    fun fetchDay(day: LocalDate, key: String): List<MatchInfo>? {
        if (key.isBlank()) return null
        val zone = URLEncoder.encode(ZoneId.systemDefault().id, "UTF-8")
        val body = MatchApi.getWith("$BASE/fixtures?date=$day&timezone=$zone", headers(key)) ?: return null
        return try {
            val root = JSONObject(body)
            if ((root.optJSONObject("errors")?.length() ?: 0) > 0) return null
            val rows = root.optJSONArray("response") ?: return emptyList()
            val out = ArrayList<MatchInfo>()
            for (i in 0 until rows.length()) {
                val m = parse(rows.optJSONObject(i) ?: continue) ?: continue
                if (MatchLeagues.allowed(m)) out.add(m)
            }
            MatchApi.localDay(day, out)
        } catch (e: Throwable) {
            null
        }
    }

    fun fetchOne(id: String, key: String): MatchInfo? {
        if (key.isBlank()) return null
        val body = MatchApi.getWith("$BASE/fixtures?id=$id", headers(key)) ?: return null
        return try {
            val root = JSONObject(body)
            if ((root.optJSONObject("errors")?.length() ?: 0) > 0) return null
            val row = root.optJSONArray("response")?.optJSONObject(0) ?: return null
            parse(row)
        } catch (e: Throwable) {
            null
        }
    }

    private fun parse(row: JSONObject): MatchInfo? {
        val fixture = row.optJSONObject("fixture") ?: return null
        val league = row.optJSONObject("league") ?: return null
        val teams = row.optJSONObject("teams") ?: return null
        val goals = row.optJSONObject("goals")
        val kickoff = fixture.optLong("timestamp") * 1000L
        if (kickoff <= 0L) return null
        val status = fixture.optJSONObject("status")
        val short = status?.optString("short").orEmpty()
        val elapsed = if (status == null || status.isNull("elapsed")) 0 else status.optInt("elapsed")
        val state = when (short) {
            "1H", "HT", "2H", "ET", "BT", "P", "INT", "LIVE", "SUSP" -> "in"
            "FT", "AET", "PEN", "AWD", "WO", "PST", "CANC", "ABD" -> "post"
            else -> "pre"
        }
        val detail = when (short) {
            "HT" -> "HT"
            "1H", "2H", "ET", "LIVE", "INT", "SUSP", "BT", "P" -> if (elapsed > 0) "$elapsed'" else short
            "FT", "AET", "PEN" -> short
            "PST" -> "Postponed"
            "CANC" -> "Cancelled"
            "ABD" -> "Abandoned"
            "AWD", "WO" -> "Awarded"
            else -> ""
        }
        val home = team(teams.optJSONObject("home"), goals, "home", state) ?: return null
        val away = team(teams.optJSONObject("away"), goals, "away", state) ?: return null
        return MatchInfo(
            id = "a:" + fixture.optLong("id"),
            leagueKey = "a:" + league.optLong("id"),
            leagueName = league.optString("name"),
            country = league.optString("country"),
            kickoff = kickoff,
            state = state,
            detail = detail,
            minute = elapsed,
            home = home,
            away = away
        )
    }

    private fun team(t: JSONObject?, goals: JSONObject?, side: String, state: String): MatchTeam? {
        if (t == null) return null
        val name = t.optString("name")
        if (name.isBlank()) return null
        val score = if (state == "pre" || goals == null || goals.isNull(side)) null else goals.optInt(side)
        return MatchTeam(
            name = name,
            abbr = name.filter { it.isLetter() }.take(3).uppercase(),
            logo = t.optString("logo"),
            score = score
        )
    }
}
