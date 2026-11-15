package com.dang.glancecaps.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SPEAKER) return
        CallAudio.toggle(context)
    }

    companion object {
        const val ACTION_SPEAKER = "com.dang.glancecaps.CALL_SPEAKER"
    }
}
