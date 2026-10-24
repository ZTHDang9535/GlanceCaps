package com.dang.glancecaps.island

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.dang.glancecaps.CrashLog
import com.dang.glancecaps.battery.BatteryReader
import com.dang.glancecaps.battery.ChargeEstimator
import com.dang.glancecaps.data.ModulePrefs
import com.dang.glancecaps.match.MatchTracker
import com.dang.glancecaps.weather.AlertTracker
import com.dang.glancecaps.weather.WeatherSchedule
import com.dang.glancecaps.weather.WeatherTracker
import com.dang.glancecaps.steps.StepSensor
import com.dang.glancecaps.steps.StepStore
import com.dang.glancecaps.steps.Steps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class GlanceCapsService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private val estimator = ChargeEstimator()
    private lateinit var stepSensor: StepSensor
    private lateinit var connMonitor: ConnectivityMonitor
    private var matchShown = false
    private var lastMatchState = ""
    private var weatherShown = false
    private var lastWeatherSig = ""
    private var alertShown = false
    private var lastAlertSig = ""
    private var lastAlertId = ""
    private var lastMatchSig = ""
    private var lastScoreKey = ""
    private var matchFinishedAt = 0L
    private var lastEta: Int? = null
    private var lastEtaAt = 0L
    private var shownW = 0f
    private var shownWAt = 0L
    private var walkBaseAt = 0L
    private var walkBaseSteps = 0
    private var walkingUntil = 0L

    private var chargingShown = false
    private var tempShown = false
    private var stepsShown = false
    private var lastServiceText = ""
    private var lastChargingSig = ""
    private var lastTempSig = ""
    private var lastStepsSig = ""
    private var smoothW = 0f
    private var lastSteps = -1
    private var lastStepChangeAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        stepSensor = StepSensor(this)
        connMonitor = ConnectivityMonitor(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        try {
            val p = ModulePrefs(this)
            GlanceCapsNotifications.ensureChannels(this)
            val n = GlanceCapsNotifications.serviceNotification(this, p)
            lastServiceText = n.extras.getCharSequence("android.text")?.toString() ?: ""
            ServiceCompat.startForeground(
                this, GlanceCapsNotifications.ID_SERVICE, n,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } catch (e: Throwable) {
            CrashLog.append(this, "service.startForeground", e)
            stopSelf()
            return START_NOT_STICKY
        }

        if (job == null) {
            job = scope.launch {
                while (isActive) {
                    try {
                        tick()
                    } catch (e: Throwable) {
                        CrashLog.append(this@GlanceCapsService, "service.tick", e)
                    }
                    delay(3000)
                }
            }
        }
        return START_STICKY
    }

    private fun tick() {
        val p = ModulePrefs(this)
        if (!p.chargingEnabled && !p.tempEnabled && !p.stepsEnabled && !p.connEnabled && !p.matchEnabled && !p.weatherEnabled) {
            stopSelf()
            return
        }
        val nm = getSystemService(NotificationManager::class.java)
        if (p.connEnabled) connMonitor.start() else connMonitor.stop()
        val raw = BatteryReader.read(this)
        val tNow = SystemClock.elapsedRealtime()
        if (!raw.charging) {
            estimator.reset()
            smoothW = 0f
            shownW = 0f
            lastEta = null
        }

        smoothW = if (smoothW == 0f) raw.watts else smoothW * 0.6f + raw.watts * 0.4f
        val shownNow = if (shownW > 0f && Math.abs(smoothW - shownW) < 0.5f && tNow - shownWAt < 20_000L) {
            shownW
        } else {
            shownW = smoothW
            shownWAt = tNow
            smoothW
        }
        val s = raw.copy(watts = shownNow)
        val rawEta = estimator.etaMinutes(raw)
        if (rawEta != null) {
            lastEta = rawEta
            lastEtaAt = tNow
        }
        val eta = rawEta ?: lastEta.takeIf { s.charging && tNow - lastEtaAt < 120_000L }
        val live = !GlanceCapsNotifications.useHyper(p)
        val merged = p.tempEnabled && p.tempMerge && !live
        val hold = ConnectivityMonitor.showing()

        if (p.chargingEnabled && s.charging) {
            val sig = GlanceCapsNotifications.chargingSignature(this, s, eta, p, merged)
            if ((!chargingShown || sig != lastChargingSig) && !hold) {

                nm.notify(
                    GlanceCapsNotifications.ID_CHARGING,
                    GlanceCapsNotifications.charging(this, s, eta, p, merged, expand = !chargingShown)
                )
                lastChargingSig = sig
                chargingShown = true
            }
        } else {
            if (chargingShown) nm.cancel(GlanceCapsNotifications.ID_CHARGING)
            chargingShown = false
            lastChargingSig = ""
        }

        val showTemp = p.tempEnabled && (live || !p.tempMerge) && (!p.tempOnlyCharging || s.charging)
        if (showTemp) {
            val sig = GlanceCapsNotifications.temperatureSignature(s, p)
            if ((!tempShown || sig != lastTempSig) && !hold) {
                nm.notify(
                    GlanceCapsNotifications.ID_TEMP,
                    GlanceCapsNotifications.temperature(this, s, p, expand = !tempShown)
                )
                lastTempSig = sig
                tempShown = true
            }
        } else {
            if (tempShown) nm.cancel(GlanceCapsNotifications.ID_TEMP)
            tempShown = false
            lastTempSig = ""
        }

        if (p.stepsEnabled) {
            stepSensor.start()
            val r = Steps.read(this)
            val now = SystemClock.elapsedRealtime()
            if (r != null) {
                if (r.steps != lastSteps) {
                    if (lastSteps >= 0) lastStepChangeAt = now
                    lastSteps = r.steps
                }
                if (now - walkBaseAt > 60_000L || r.steps < walkBaseSteps) {
                    walkBaseAt = now
                    walkBaseSteps = r.steps
                }
                if (r.steps - walkBaseSteps >= 12) walkingUntil = now + 3 * 60_000L
                val walking = now < walkingUntil
                if (p.stepAlways || walking) {
                    val goalHit = r.steps >= p.stepGoal && p.stepPopOnGoal && StepStore.markGoalOnce(this)
                    val sig = GlanceCapsNotifications.stepsSignature(r.steps, p)
                    if ((!stepsShown || sig != lastStepsSig || goalHit) && !hold) {
                        nm.notify(
                            GlanceCapsNotifications.ID_STEPS,
                            GlanceCapsNotifications.steps(this, r.steps, p, expand = (!stepsShown && p.stepAlways) || goalHit)
                        )
                        lastStepsSig = sig
                        stepsShown = true
                    }
                } else {
                    if (stepsShown) nm.cancel(GlanceCapsNotifications.ID_STEPS)
                    stepsShown = false
                    lastStepsSig = ""
                }
            } else {
                if (stepsShown) nm.cancel(GlanceCapsNotifications.ID_STEPS)
                stepsShown = false
                lastStepsSig = ""
            }
        } else {
            stepSensor.stop()
            if (stepsShown) nm.cancel(GlanceCapsNotifications.ID_STEPS)
            stepsShown = false
            lastStepsSig = ""
            lastSteps = -1
            walkBaseAt = 0L
            walkingUntil = 0L
        }

        if (p.matchEnabled && p.matchId.isNotBlank()) {
            MatchTracker.refresh(this, p)
            val m = MatchTracker.current
            if (m != null) {
                val clockNow = SystemClock.elapsedRealtime()
                if (m.finished) {
                    if (matchFinishedAt == 0L) matchFinishedAt = clockNow
                } else {
                    matchFinishedAt = 0L
                }
                val expired = m.finished && clockNow - matchFinishedAt > 10 * 60_000L
                if (expired) {
                    if (matchShown) nm.cancel(GlanceCapsNotifications.ID_MATCH)
                    matchShown = false
                    lastMatchSig = ""
                } else if (!hold) {
                    val sig = GlanceCapsNotifications.matchSignature(this, m, p, MatchTracker.pair != null)
                    if (!matchShown || sig != lastMatchSig) {
                        val scoreKey = m.homeScore + "-" + m.awayScore
                        val goal = matchShown && m.live && (scoreKey != lastScoreKey || lastMatchState == "pre")
                        nm.notify(
                            GlanceCapsNotifications.ID_MATCH,
                            GlanceCapsNotifications.match(this, m, MatchTracker.home, MatchTracker.away, MatchTracker.pair, p, !matchShown || goal)
                        )
                        lastScoreKey = scoreKey
                        lastMatchState = m.state
                        lastMatchSig = sig
                        matchShown = true
                    }
                }
            }
        } else {
            if (matchShown) nm.cancel(GlanceCapsNotifications.ID_MATCH)
            matchShown = false
            lastMatchSig = ""
            matchFinishedAt = 0L
        }

        if (p.weatherEnabled && (p.weatherLat.isNotBlank() || p.weatherSource == "system") && WeatherSchedule.active(p)) {
            WeatherTracker.refresh(this, p)
            val w = WeatherTracker.current
            if (w != null && !hold) {
                val sig = GlanceCapsNotifications.weatherSignature(this, w, p)
                if (!weatherShown || sig != lastWeatherSig) {
                    nm.notify(GlanceCapsNotifications.ID_WEATHER, GlanceCapsNotifications.weather(this, w, p, !weatherShown))
                    lastWeatherSig = sig
                    weatherShown = true
                }
            }
        } else {
            if (weatherShown) nm.cancel(GlanceCapsNotifications.ID_WEATHER)
            weatherShown = false
            lastWeatherSig = ""
        }

        if (p.weatherEnabled && p.alertsEnabled && p.weatherLat.isNotBlank()) {
            AlertTracker.refresh(this, p)
            val a = AlertTracker.current
            if (a != null && !hold) {
                val sig = GlanceCapsNotifications.alertSignature(a, p)
                if (!alertShown || sig != lastAlertSig) {
                    nm.notify(
                        GlanceCapsNotifications.ID_ALERT,
                        GlanceCapsNotifications.alert(this, a, p, expand = !alertShown || a.id != lastAlertId)
                    )
                    lastAlertSig = sig
                    lastAlertId = a.id
                    alertShown = true
                }
            } else if (a == null) {
                if (alertShown) nm.cancel(GlanceCapsNotifications.ID_ALERT)
                alertShown = false
                lastAlertSig = ""
                lastAlertId = ""
            }
        } else {
            if (alertShown) nm.cancel(GlanceCapsNotifications.ID_ALERT)
            alertShown = false
            lastAlertSig = ""
            lastAlertId = ""
        }

        val sn = GlanceCapsNotifications.serviceNotification(this, p)
        val text = sn.extras.getCharSequence("android.text")?.toString() ?: ""
        if (text != lastServiceText) {
            lastServiceText = text
            nm.notify(GlanceCapsNotifications.ID_SERVICE, sn)
        }
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        try { stepSensor.stop() } catch (e: Throwable) { }
        try { connMonitor.stop() } catch (e: Throwable) { }
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.cancel(GlanceCapsNotifications.ID_CHARGING)
            nm.cancel(GlanceCapsNotifications.ID_TEMP)
            nm.cancel(GlanceCapsNotifications.ID_STEPS)
            nm.cancel(GlanceCapsNotifications.ID_MATCH)
            nm.cancel(GlanceCapsNotifications.ID_WEATHER)
            nm.cancel(GlanceCapsNotifications.ID_ALERT)
        } catch (e: Throwable) { }
        super.onDestroy()
    }

    companion object {

        fun sync(ctx: Context) {
            try {
                val p = ModulePrefs(ctx)
                val intent = Intent(ctx, GlanceCapsService::class.java)
                if (p.chargingEnabled || p.tempEnabled || p.stepsEnabled || p.connEnabled || p.matchEnabled || p.weatherEnabled) {
                    ContextCompat.startForegroundService(ctx, intent)
                } else {
                    ctx.stopService(intent)
                }
            } catch (e: Throwable) {

                CrashLog.append(ctx, "service.sync", e)
            }
        }
    }
}
