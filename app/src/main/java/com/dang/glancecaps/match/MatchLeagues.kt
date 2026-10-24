package com.dang.glancecaps.match

object MatchLeagues {
    private val common = listOf(
        "women", "feminin", "femenin", "frauen", "u15", "u16", "u17", "u18", "u19", "u20", "u21", "u22", "u23",
        "youth", "reserve", "futsal", "beach", "amateur", "primavera"
    )

    private class League(
        val label: String,
        val country: String,
        val names: List<String>,
        val exclude: List<String> = common,
    ) {
        fun matches(country: String, name: String): Boolean {
            if (this.country.isNotEmpty() && this.country != country) return false
            if (exclude.any { name.contains(it) }) return false
            return names.any { if (it.startsWith("=")) name == it.substring(1) else name.contains(it) }
        }
    }

    private val leagues = listOf(
        League("V.League 1", "vietnam", listOf("v.league 1", "v-league 1", "v league 1", "=v.league")),
        League("V.League 2", "vietnam", listOf("v.league 2", "v-league 2", "v league 2")),
        League("Vietnam Cup", "vietnam", listOf("cup"), listOf("women", "u19", "u21", "u23", "youth", "futsal")),
        League("ASEAN Championship", "", listOf("asean championship", "aff championship", "aff cup", "asean cup")),
        League("ASEAN Club Championship", "", listOf("asean club championship", "shopee cup", "aff club championship")),
        League("SEA Games", "", listOf("sea games", "southeast asian games"), listOf("women")),
        League("AFF U23 Championship", "", listOf("aff u23", "aff u-23"), listOf("women")),
        League("Premier League", "england", listOf("=premier league")),
        League("Championship", "england", listOf("=championship")),
        League("League One", "england", listOf("=league one", "=league 1")),
        League("League Two", "england", listOf("=league two", "=league 2")),
        League("FA Cup", "england", listOf("fa cup")),
        League("EFL Cup", "england", listOf("efl cup", "carabao cup", "league cup")),
        League("LaLiga", "spain", listOf("=laliga", "=la liga", "=primera division", "=primera división")),
        League("Serie A", "italy", listOf("=serie a")),
        League("Bundesliga", "germany", listOf("=bundesliga")),
        League("FIFA World Cup", "", listOf("world cup"), common + "club"),
        League("UEFA Champions League", "", listOf("uefa champions league"), common + listOf("qualification", "afc", "caf", "concacaf")),
        League("UEFA Europa League", "", listOf("uefa europa league"), common + "qualification"),
        League("UEFA Conference League", "", listOf("conference league"), common + "qualification"),
        League("UEFA Euro", "", listOf("=euro", "euro, qualification", "euro qualification", "european championship", "uefa euro", "euro championship")),
        League("UEFA Nations League", "", listOf("nations league")),
        League("AFC Asian Cup", "", listOf("asian cup"))
    )

    val size: Int get() = leagues.size

    fun rank(country: String, leagueName: String): Int {
        val c = country.trim().lowercase()
        val n = leagueName.trim().lowercase()
        if (n.isEmpty()) return -1
        return leagues.indexOfFirst { it.matches(c, n) }
    }

    fun allowed(m: MatchInfo): Boolean = rank(m.country, m.leagueName) >= 0
}
