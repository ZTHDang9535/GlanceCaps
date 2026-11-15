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
        League("Premier League", "england", listOf("=premier league")),
        League("LaLiga", "spain", listOf("=laliga", "=la liga", "=primera division", "=primera división")),
        League("Serie A", "italy", listOf("=serie a")),
        League("Bundesliga", "germany", listOf("=bundesliga")),
        League("Ligue 1", "france", listOf("=ligue 1")),
        League("UEFA Champions League", "", listOf("uefa champions league"), common + listOf("qualification", "afc", "caf", "concacaf")),
        League("UEFA Europa League", "", listOf("uefa europa league"), common + "qualification"),
        League("UEFA Conference League", "", listOf("conference league"), common + "qualification"),
        League("FIFA World Cup", "", listOf("world cup"), common + "club"),
        League("UEFA Euro", "", listOf("=euro", "euro, qualification", "euro qualification", "european championship", "uefa euro", "euro championship")),
        League("UEFA Nations League", "", listOf("nations league")),
        League("Copa America", "", listOf("copa america", "copa américa")),
        League("FA Cup", "england", listOf("fa cup")),
        League("EFL Cup", "england", listOf("efl cup", "carabao cup", "league cup")),
        League("Copa del Rey", "spain", listOf("copa del rey")),
        League("Coppa Italia", "italy", listOf("coppa italia")),
        League("DFB-Pokal", "germany", listOf("dfb-pokal", "dfb pokal")),
        League("Coupe de France", "france", listOf("coupe de france")),
        League("Championship", "england", listOf("=championship")),
        League("Eredivisie", "netherlands", listOf("=eredivisie")),
        League("Primeira Liga", "portugal", listOf("=primeira liga", "=liga portugal")),
        League("Saudi Pro League", "saudi arabia", listOf("saudi pro league", "roshn saudi league")),
        League("MLS", "", listOf("=mls", "=major league soccer")),
        League("Copa Libertadores", "", listOf("copa libertadores", "libertadores")),
        League("International Friendly", "", listOf("international friendl", "=friendlies", "=friendly"), common + "club")
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
