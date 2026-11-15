package com.dang.glancecaps.call

import com.dang.glancecaps.island.CallAvatar
import android.graphics.drawable.Icon
import android.graphics.Bitmap
import android.app.Person
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.dang.glancecaps.data.ModulePrefs
import com.dang.glancecaps.island.GlanceCapsNotifications
import com.dang.glancecaps.island.IslandMarquee
import com.dang.glancecaps.island.IslandPoster

enum class CallState { INCOMING, OUTGOING, ONGOING }

data class CallInfo(
    val key: String,
    val pkg: String,
    val name: String,
    val state: CallState,
    val startedAt: Long,
    val hangUp: PendingIntent?,
    val answer: PendingIntent?,
    val decline: PendingIntent?,
    val speaker: PendingIntent?,
    val avatar: Bitmap? = null,
)

object CallAccess {
    fun granted(ctx: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    fun openSettings(ctx: Context) {
        try {
            ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Throwable) {
        }
    }
}

object CallParser {
    private val skipped = setOf(
        "com.android.incallui", "com.android.contacts", "com.android.phone", "com.android.server.telecom",
        "com.android.deskclock", "com.google.android.deskclock", "com.miui.deskclock"
    )
    private val hangWords = Regex(
        "\\b(hang ?up|end( call)?|decline|reject|dismiss|cancel|leave)\\b|từ chối|kết thúc|cúp|ngắt",
        RegexOption.IGNORE_CASE
    )
    private val answerWords = Regex("\\b(answer|accept|pick ?up)\\b|trả lời|nghe máy", RegexOption.IGNORE_CASE)
    private val speakerWords = Regex("\\b(speaker(phone)?|loudspeaker|loa)\\b", RegexOption.IGNORE_CASE)
    private val channelWords = Regex("call|voip|ringing", RegexOption.IGNORE_CASE)
    private val incomingWords = Regex(
        "incoming|is calling|calling you|cuộc gọi đến|đang gọi cho bạn|gọi đến|gọi video đến|gọi thoại đến",
        RegexOption.IGNORE_CASE
    )
    private val connectedWords = Regex(
        "ongoing (voice |video )?call|in call|on call|call in progress|đang trong cuộc gọi|cuộc gọi đang diễn ra",
        RegexOption.IGNORE_CASE
    )
    private val timerText = Regex("\\b\\d{1,2}:\\d{2}(:\\d{2})?\\b")

    @Suppress("DEPRECATION")
    private fun pending(n: Notification, key: String): PendingIntent? = try {
        n.extras.getParcelable<PendingIntent>(key)
    } catch (e: Throwable) {
        null
    }

    @Suppress("DEPRECATION")
    private fun personIcon(n: Notification): Icon? = try {
        n.extras.getParcelable<Person>("android.callPerson")?.icon
    } catch (e: Throwable) {
        null
    }

    private fun avatarOf(ctx: Context, n: Notification): Bitmap? {
        if (!ModulePrefs(ctx).callShowAvatar) return null
        return try {
            val icon = n.getLargeIcon() ?: personIcon(n)
            icon?.loadDrawable(ctx)?.let { CallAvatar.fromDrawable(it) }
        } catch (e: Throwable) {
            null
        }
    }

    fun parse(ctx: Context, sbn: StatusBarNotification, includePhone: Boolean): CallInfo? {
        if (sbn.packageName == ctx.packageName) return null
        if (!includePhone && sbn.packageName in skipped) return null
        val n = sbn.notification
        val ex = n.extras
        val type = ex.getInt("android.callType", 0)
        val template = ex.getString(Notification.EXTRA_TEMPLATE).orEmpty()
        val ongoingFlag = n.flags and Notification.FLAG_ONGOING_EVENT != 0
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = listOfNotNull(
            ex.getCharSequence(Notification.EXTRA_TEXT),
            ex.getCharSequence(Notification.EXTRA_SUB_TEXT),
            ex.getCharSequence(Notification.EXTRA_BIG_TEXT)
        ).joinToString(" ")
        val everything = "$title $text"
        val actions = n.actions?.toList().orEmpty()
        fun find(re: Regex): PendingIntent? = actions.firstOrNull { re.containsMatchIn(it.title ?: "") }?.actionIntent

        val answerAction = find(answerWords)
        val hangAction = find(hangWords)
        val chrono = ex.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)
        val fullScreen = n.fullScreenIntent != null
        val active = ongoingFlag || fullScreen || type != 0 || actions.isNotEmpty()

        val byCategory = n.category == Notification.CATEGORY_CALL
        val byStyle = type != 0 || template.endsWith("CallStyle")
        val byChannel = n.channelId?.let { channelWords.containsMatchIn(it) } == true
        val byActions = answerAction != null && hangAction != null
        val byText = incomingWords.containsMatchIn(everything) && (ongoingFlag || fullScreen)
        val byTimer = chrono && hangAction != null
        val accepted = byStyle || byActions || byTimer || byText ||
            (byCategory && (ongoingFlag || fullScreen)) || (byChannel && active)
        if (!accepted) return null

        val connected = chrono || connectedWords.containsMatchIn(everything) || timerText.containsMatchIn(text)
        val answer = pending(n, "android.answerIntent") ?: answerAction
        val state = when {
            type == 1 -> CallState.INCOMING
            type == 2 || type == 3 -> if (connected) CallState.ONGOING else CallState.OUTGOING
            answer != null || (!connected && (fullScreen || incomingWords.containsMatchIn(everything))) -> CallState.INCOMING
            connected -> CallState.ONGOING
            else -> CallState.OUTGOING
        }

        val name = title.ifBlank {
            try {
                val info = ctx.packageManager.getApplicationInfo(sbn.packageName, 0)
                ctx.packageManager.getApplicationLabel(info).toString()
            } catch (e: Throwable) {
                sbn.packageName
            }
        }

        return CallInfo(
            key = sbn.key,
            pkg = sbn.packageName,
            name = name,
            state = state,
            startedAt = if (chrono) n.`when` else 0L,
            hangUp = pending(n, "android.hangUpIntent") ?: hangAction,
            answer = answer,
            decline = pending(n, "android.declineIntent") ?: hangAction,
            speaker = find(speakerWords),
            avatar = avatarOf(ctx, n)
        )
    }
}

object CallTracker {
    @Volatile
    var current: CallInfo? = null
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var ticker: Runnable? = null
    private var lastSig = ""

    private fun signature(ctx: Context, info: CallInfo): String =
        info.state.name + "|" + GlanceCapsNotifications.callStatus(info, ModulePrefs(ctx)) + "|" + CallAudio.speakerOn(ctx)

    fun accept(ctx: Context, info: CallInfo) {
        val app = ctx.applicationContext
        val prev = current
        val started = when {
            info.state != CallState.ONGOING -> 0L
            info.startedAt > 0L -> info.startedAt
            prev != null && prev.key == info.key && prev.state == CallState.ONGOING -> prev.startedAt
            else -> System.currentTimeMillis()
        }
        val next = info.copy(startedAt = started)
        current = next
        val expand = prev == null || prev.key != next.key ||
            (next.state == CallState.INCOMING && prev.state != CallState.INCOMING)
        post(app, next, expand)
        lastSig = signature(app, next)
        schedule(app)
    }

    fun end(ctx: Context, key: String? = null) {
        val cur = current ?: return
        if (key != null && key != cur.key) return
        current = null
        ticker?.let { handler.removeCallbacks(it) }
        ticker = null
        IslandMarquee.stop(GlanceCapsNotifications.ID_CALL)
        try {
            ctx.getSystemService(NotificationManager::class.java).cancel(GlanceCapsNotifications.ID_CALL)
        } catch (e: Throwable) {
        }
    }

    private fun post(ctx: Context, info: CallInfo, expand: Boolean) {
        val p = ModulePrefs(ctx)
        IslandPoster.post(ctx, GlanceCapsNotifications.ID_CALL, GlanceCapsNotifications.call(ctx, info, p, expand))
    }

    private fun schedule(ctx: Context) {
        ticker?.let { handler.removeCallbacks(it) }
        ticker = null
        if (current == null) return
        val task = object : Runnable {
            override fun run() {
                val c = current ?: return
                val sig = signature(ctx, c)
                if (sig != lastSig) {
                    lastSig = sig
                    post(ctx, c, false)
                }
                handler.postDelayed(this, 1000L)
            }
        }
        ticker = task
        handler.postDelayed(task, 1000L)
    }
}
