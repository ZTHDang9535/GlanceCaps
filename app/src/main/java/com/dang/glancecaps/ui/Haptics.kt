package com.dang.glancecaps.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

object Haptics {
    @Volatile
    var enabled: Boolean = true

    fun tick(view: View) {
        if (enabled) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun toggle(view: View, on: Boolean) {
        if (!enabled) return
        if (Build.VERSION.SDK_INT >= 34) {
            view.performHapticFeedback(
                if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF
            )
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    fun confirm(view: View) {
        if (!enabled) return
        if (Build.VERSION.SDK_INT >= 30) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        else view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }
}
