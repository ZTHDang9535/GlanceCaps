package com.dang.glancecaps.island

import org.json.JSONObject

object IslandPadding {
    private const val FIG = "\u2007"
    private const val ICON_UNITS = 3

    private fun contentUnits(content: String): Int = (content.length * 3 + 3) / 4

    private fun width(side: JSONObject?): Int {
        if (side == null) return 0
        val text = side.optJSONObject("textInfo")
        val title = text?.optString("title").orEmpty()
        val content = text?.optString("content").orEmpty()
        val body = maxOf(title.length, contentUnits(content))
        return body + if (side.has("picInfo")) ICON_UNITS else 0
    }

    private fun pad(side: JSONObject, extra: Int, leading: Boolean) {
        if (extra <= 0) return
        val text = side.optJSONObject("textInfo") ?: JSONObject().also { side.put("textInfo", it) }
        val title = text.optString("title")
        val content = text.optString("content")
        if (content.isNotEmpty() && contentUnits(content) > title.length) {
            text.put("content", content + FIG.repeat((extra * 4 + 2) / 3))
            return
        }
        val filler = FIG.repeat(extra)
        text.put("title", if (leading) filler + title else title + filler)
    }

    fun apply(big: JSONObject, auto: Boolean, length: Int) {
        if (auto) return
        val left = big.optJSONObject("imageTextInfoLeft") ?: return
        var right = big.optJSONObject("imageTextInfoRight")
        if (right == null) {
            right = JSONObject().put("type", 2).put("textInfo", JSONObject().put("title", FIG))
            big.put("imageTextInfoRight", right)
        }
        val wl = width(left)
        val wr = width(right)
        val target = length
        pad(left, target - wl, false)
        pad(right, target - wr, true)
    }
}
