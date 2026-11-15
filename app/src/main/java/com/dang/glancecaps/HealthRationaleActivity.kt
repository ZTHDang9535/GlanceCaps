package com.dang.glancecaps

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import com.dang.glancecaps.i18n.tr

class HealthRationaleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        val view = TextView(this)
        view.text = tr("GlanceCaps reads your step count from Health Connect so it can show it on the island.") + " " +
            tr("Only today's step total from Mi Fitness is read. Nothing is stored or sent anywhere.")
        view.textSize = 16f
        view.setPadding(pad, pad, pad, pad)
        setContentView(view)
    }
}
