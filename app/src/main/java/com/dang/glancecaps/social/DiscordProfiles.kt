package com.dang.glancecaps.social

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object DiscordProfiles {
    const val USER_ID = "843050556880191489"

    class Profile(
        val name: String,
        val username: String,
        val pronouns: String,
        val bio: String,
        val avatarUrl: String,
        val bannerUrl: String,
        val bannerColor: Int?,
    )

    val fallback = Profile(
        name = "Dang",
        username = "dang_9535",
        pronouns = "He/Him",
        bio = "local name = \"Dang\" or \"Dang9535\"\nlocal time = \"GMT+7\"\nbyw > btw\n\nlocal fav = \"Kasane Teto\" and \"Music\" and \"Deving\"\nlocal gun = https://guns.lol/Dang9535",
        avatarUrl = "",
        bannerUrl = "",
        bannerColor = null
    )

    private fun get(url: String): String? {
        var c: HttpURLConnection? = null
        return try {
            c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.setRequestProperty("User-Agent", "GlanceCaps/1.0")
            c.setRequestProperty("Accept", "application/json")
            if (c.responseCode != 200) null else c.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Throwable) {
            null
        } finally {
            c?.disconnect()
        }
    }

    fun fetch(): Profile? {
        if (USER_ID.isBlank()) return null
        return fetchDstn() ?: fetchLookup()
    }

    private fun color(hex: String?): Int? {
        if (hex.isNullOrBlank()) return null
        return try {
            android.graphics.Color.parseColor(if (hex.startsWith("#")) hex else "#$hex")
        } catch (e: Throwable) {
            null
        }
    }

    private fun fetchDstn(): Profile? {
        val body = get("https://dcdn.dstn.to/profile/$USER_ID") ?: return null
        return try {
            val root = JSONObject(body)
            val user = root.optJSONObject("user") ?: return null
            val profile = root.optJSONObject("user_profile")
            val username = user.optString("username")
            val avatar = user.optString("avatar")
            val banner = user.optString("banner")
            val accent = if (user.isNull("accent_color")) null else user.optInt("accent_color")
            Profile(
                name = user.optString("global_name").ifBlank { username },
                username = username,
                pronouns = profile?.optString("pronouns").orEmpty(),
                bio = profile?.optString("bio").orEmpty(),
                avatarUrl = if (avatar.isBlank() || avatar == "null") "" else "https://cdn.discordapp.com/avatars/$USER_ID/$avatar.png?size=256",
                bannerUrl = if (banner.isBlank() || banner == "null") "" else "https://cdn.discordapp.com/banners/$USER_ID/$banner.png?size=600",
                bannerColor = color(user.optString("banner_color")) ?: accent?.let { 0xFF000000.toInt() or it }
            )
        } catch (e: Throwable) {
            null
        }
    }

    private fun fetchLookup(): Profile? {
        val body = get("https://discordlookup.mesalytic.moe/v1/user/$USER_ID") ?: return null
        return try {
            val root = JSONObject(body)
            val username = root.optString("username")
            if (username.isBlank()) return null
            val banner = root.optJSONObject("banner")
            Profile(
                name = root.optString("global_name").ifBlank { username },
                username = username,
                pronouns = "",
                bio = "",
                avatarUrl = root.optJSONObject("avatar")?.optString("link").orEmpty().let { if (it == "null") "" else it },
                bannerUrl = banner?.optString("link").orEmpty().let { if (it == "null") "" else it },
                bannerColor = color(banner?.optString("color"))
            )
        } catch (e: Throwable) {
            null
        }
    }

    fun image(url: String, maxWidth: Int): Bitmap? {
        if (url.isBlank()) return null
        var c: HttpURLConnection? = null
        return try {
            c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.setRequestProperty("User-Agent", "GlanceCaps/1.0")
            val raw = c.inputStream.use { BitmapFactory.decodeStream(it) } ?: return null
            if (raw.width <= maxWidth) raw else {
                val h = raw.height * maxWidth / raw.width
                Bitmap.createScaledBitmap(raw, maxWidth, h, true)
            }
        } catch (e: Throwable) {
            null
        } finally {
            c?.disconnect()
        }
    }
}
