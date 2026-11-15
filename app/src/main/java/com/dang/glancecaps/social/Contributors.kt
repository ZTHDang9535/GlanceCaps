package com.dang.glancecaps.social

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.File

object Contributors {
    class Entry(
        val userId: String,
        val role: String,
        val fallback: DiscordProfiles.Profile,
        val fallbackAvatar: String,
    )

    val all = listOf(
        Entry(
            userId = "613210299440431105",
            role = "Spanish translation",
            fallback = DiscordProfiles.Profile(
                name = "Benyamin",
                username = "yamindp",
                pronouns = "",
                bio = "",
                avatarUrl = "",
                bannerUrl = "",
                bannerColor = null
            ),
            fallbackAvatar = "contributor_613210299440431105.png"
        )
    )

    fun fallbackAvatar(ctx: Context, entry: Entry): Bitmap? = try {
        ctx.assets.open(entry.fallbackAvatar).use { BitmapFactory.decodeStream(it) }
    } catch (e: Throwable) {
        null
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("contributors", Context.MODE_PRIVATE)

    private fun avatarFile(ctx: Context, userId: String) = File(ctx.cacheDir, "contributor_$userId.png")

    fun cachedProfile(ctx: Context, userId: String): DiscordProfiles.Profile? {
        val raw = prefs(ctx).getString("profile_$userId", null) ?: return null
        return try {
            val o = JSONObject(raw)
            DiscordProfiles.Profile(
                name = o.optString("name"),
                username = o.optString("username"),
                pronouns = o.optString("pronouns"),
                bio = o.optString("bio"),
                avatarUrl = o.optString("avatarUrl"),
                bannerUrl = o.optString("bannerUrl"),
                bannerColor = if (o.has("bannerColor")) o.optInt("bannerColor") else null
            )
        } catch (e: Throwable) {
            null
        }
    }

    fun cachedAvatar(ctx: Context, userId: String): Bitmap? {
        val f = avatarFile(ctx, userId)
        return if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
    }

    fun refresh(ctx: Context, userId: String): Pair<DiscordProfiles.Profile, Bitmap?>? {
        val p = DiscordProfiles.fetch(userId) ?: return null
        val o = JSONObject()
            .put("name", p.name)
            .put("username", p.username)
            .put("pronouns", p.pronouns)
            .put("bio", p.bio)
            .put("avatarUrl", p.avatarUrl)
            .put("bannerUrl", p.bannerUrl)
        p.bannerColor?.let { o.put("bannerColor", it) }
        prefs(ctx).edit().putString("profile_$userId", o.toString()).apply()
        val avatar = DiscordProfiles.image(p.avatarUrl, 256)
        if (avatar != null) {
            try {
                avatarFile(ctx, userId).outputStream().use { avatar.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } catch (e: Throwable) {
            }
        }
        return p to avatar
    }
}
