package com.dang.glancecaps.call

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable

class CallApp(val pkg: String, val label: String, val icon: Bitmap, val suggested: Boolean)

object CallApps {
    private val suggestedPackages = setOf(
        "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.telegram.messenger.web",
        "com.facebook.orca", "com.facebook.katana", "com.zing.zalo", "com.viber.voip", "com.skype.raider",
        "com.discord", "org.thoughtcrime.securesms", "com.google.android.apps.tachyon",
        "com.google.android.dialer", "us.zoom.videomeetings", "com.microsoft.teams", "jp.naver.line.android",
        "com.kakao.talk", "com.tencent.mm", "com.instagram.android", "com.slack", "com.snapchat.android",
        "com.imo.android.imoim"
    )

    fun load(ctx: Context): List<CallApp> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val seen = HashSet<String>()
        val out = ArrayList<CallApp>()
        for (ri in pm.queryIntentActivities(intent, 0)) {
            val pkg = ri.activityInfo.packageName
            if (pkg == ctx.packageName || !seen.add(pkg)) continue
            out.add(CallApp(pkg, ri.loadLabel(pm).toString(), bitmap(ri.loadIcon(pm)), pkg in suggestedPackages))
        }
        return out.sortedWith(compareByDescending<CallApp> { it.suggested }.thenBy { it.label.lowercase() })
    }

    private fun bitmap(d: Drawable): Bitmap {
        val size = 96
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, size, size)
        d.draw(Canvas(bmp))
        return bmp
    }
}
