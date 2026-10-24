package com.dang.glancecaps.island

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.graphics.drawable.Icon
import android.os.Bundle
import com.dang.glancecaps.R

object LiveUpdates {
    private const val EXTRA_PROMOTED = "android.requestPromotedOngoing"
    private const val EXTRA_SHORT_TEXT = "android.shortCriticalText"

    fun supported(): Boolean = Build.VERSION.SDK_INT >= 36

    fun canPromote(ctx: Context): Boolean {
        if (!supported()) return false
        return try {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            val m = NotificationManager::class.java.getMethod("canPostPromotedNotifications")
            m.invoke(nm) as? Boolean ?: true
        } catch (e: Throwable) {
            true
        }
    }

    fun build(
        ctx: Context,
        open: PendingIntent,
        smallIcon: Int,
        title: String,
        text: String,
        shortText: String?,
        progress: Int?,
        accent: Int,
        timeoutMs: Long = 0L,
        largeIcon: android.graphics.Bitmap? = null,
        subText: String? = null,
        closeIntent: PendingIntent? = null,
    ): Notification {
        GlanceCapsNotifications.ensureChannels(ctx)
        val b = Notification.Builder(ctx, GlanceCapsNotifications.CH_LIVEUPDATE)
            .setSmallIcon(smallIcon)
            .setContentTitle(title.ifBlank { " " })
            .setContentText(text)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setColor(accent)
            .setColorized(false)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_STATUS)
        if (!supported()) b.setStyle(Notification.BigTextStyle().bigText(text))
        if (largeIcon != null) b.setLargeIcon(largeIcon)
        if (!supported() && subText != null) b.setSubText(subText)
        if (closeIntent != null) {
            b.addAction(Notification.Action.Builder(Icon.createWithResource(ctx, R.drawable.ic_close), "Close", closeIntent).build())
        }
        if (timeoutMs > 0L) b.setTimeoutAfter(timeoutMs)
        if (progress != null) {
            val styled = supported() && applyProgressStyle(b, progress.coerceIn(0, 100), accent)
            if (!styled) b.setProgress(100, progress.coerceIn(0, 100), false)
        }
        if (supported()) promote(b, shortText)
        return b.build()
    }

    private fun applyProgressStyle(b: Notification.Builder, progress: Int, accent: Int): Boolean = try {
        val styleCls = Class.forName("android.app.Notification\$ProgressStyle")
        val segmentCls = Class.forName("android.app.Notification\$ProgressStyle\$Segment")
        val style = styleCls.getConstructor().newInstance()
        val segment = segmentCls.getConstructor(Int::class.javaPrimitiveType).newInstance(100)
        segmentCls.getMethod("setColor", Int::class.javaPrimitiveType).invoke(segment, accent)
        styleCls.getMethod("setProgressSegments", List::class.java).invoke(style, listOf(segment))
        styleCls.getMethod("setProgress", Int::class.javaPrimitiveType).invoke(style, progress)
        styleCls.getMethod("setStyledByProgress", Boolean::class.javaPrimitiveType).invoke(style, true)
        b.setStyle(style as Notification.Style)
        true
    } catch (e: Throwable) {
        false
    }

    private fun promote(b: Notification.Builder, shortText: String?) {
        val extras = Bundle()
        try {
            Notification.Builder::class.java
                .getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType)
                .invoke(b, true)
        } catch (e: Throwable) {
            extras.putBoolean(EXTRA_PROMOTED, true)
        }
        if (!shortText.isNullOrBlank()) {
            try {
                Notification.Builder::class.java
                    .getMethod("setShortCriticalText", String::class.java)
                    .invoke(b, shortText)
            } catch (e: Throwable) {
                extras.putString(EXTRA_SHORT_TEXT, shortText)
            }
        }
        if (!extras.isEmpty) b.addExtras(extras)
    }
}
