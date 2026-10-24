package com.dang.glancecaps.steps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.dang.glancecaps.CrashLog
import com.dang.glancecaps.data.ModulePrefs
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class StepReading(val steps: Int, val source: String)

object Steps {
    private const val MIUI_URI = "content://com.miui.providers.steps/item"
    const val PERM_MIUI = "miui.permission.READ_STEPS"

    const val SRC_AUTO = "auto"
    const val SRC_HEALTH = "health"
    const val SRC_ZEPP = "zepp"
    const val SRC_PEDOMETER = "pedometer"
    const val SRC_SENSOR = "sensor"
    val SOURCES = listOf(SRC_HEALTH, SRC_ZEPP, SRC_PEDOMETER, SRC_SENSOR)

    data class Availability(val ok: Boolean, val reason: String?)

    private val cache = HashMap<String, Pair<Long, StepReading?>>()

    fun isXiaomi(): Boolean {
        val maker = Build.MANUFACTURER.lowercase()
        return maker.contains("xiaomi") || maker.contains("redmi") || maker.contains("poco")
    }

    fun label(key: String): String = when (key) {
        SRC_HEALTH -> "Mi Fitness"
        SRC_ZEPP -> "Zepp Life"
        SRC_PEDOMETER -> "Xiaomi pedometer"
        SRC_SENSOR -> "System step counter"
        else -> "Automatic"
    }

    fun hasActivityPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    fun hasMiuiPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, PERM_MIUI) == PackageManager.PERMISSION_GRANTED

    fun hasSensor(ctx: Context): Boolean = try {
        (ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager)
            ?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
    } catch (e: Throwable) { false }

    fun missingPermissions(ctx: Context): Array<String> {
        val list = ArrayList<String>()
        if (!hasActivityPermission(ctx)) list.add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (isXiaomi() && !hasMiuiPermission(ctx)) list.add(PERM_MIUI)
        return list.toTypedArray()
    }

    fun startOfDayMillis(): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun fromHealth(ctx: Context, key: String): StepReading? =
        HealthSteps.cached(ctx, key)?.let { StepReading(it, label(key)) }

    private fun fromMiui(ctx: Context): StepReading? = try {
        ctx.contentResolver.query(
            Uri.parse(MIUI_URI), arrayOf("_steps"), "_begin_time>=?",
            arrayOf(startOfDayMillis().toString()), null
        )?.use { c ->
            var sum = 0L
            while (c.moveToNext()) sum += c.getLong(0)
            StepReading(sum.toInt(), label(SRC_PEDOMETER))
        }
    } catch (e: Throwable) { null }

    private fun fromSensor(ctx: Context): StepReading? {
        if (!hasActivityPermission(ctx) || !hasSensor(ctx)) return null
        val steps = if (StepStore.hasData(ctx)) StepStore.today(ctx) else 0
        return StepReading(steps, label(SRC_SENSOR))
    }

    @Synchronized
    private fun cached(key: String, load: () -> StepReading?): StepReading? {
        val now = SystemClock.elapsedRealtime()
        val hit = cache[key]
        if (hit != null && now - hit.first < 10_000) return hit.second
        val r = load()
        cache[key] = now to r
        return r
    }

    private fun readFrom(ctx: Context, key: String): StepReading? = when (key) {
        SRC_HEALTH, SRC_ZEPP -> fromHealth(ctx, key)
        SRC_PEDOMETER -> if (isXiaomi() && hasMiuiPermission(ctx)) cached(SRC_PEDOMETER) { fromMiui(ctx) } else null
        SRC_SENSOR -> fromSensor(ctx)
        else -> readFrom(ctx, SRC_HEALTH) ?: readFrom(ctx, SRC_ZEPP) ?: readFrom(ctx, SRC_PEDOMETER) ?: readFrom(ctx, SRC_SENSOR)
    }

    fun availability(ctx: Context, key: String): Availability = when (key) {
        SRC_HEALTH, SRC_ZEPP -> when {
            !HealthSteps.sdkAvailable(ctx) -> Availability(false, HealthSteps.UNAVAILABLE)
            !runBlocking { HealthSteps.granted(ctx) } -> Availability(false, HealthSteps.NEEDS_PERMISSION)
            runBlocking { runCatching { HealthSteps.fetch(ctx, key) }.getOrNull() } == null -> Availability(false, HealthSteps.noData(key))
            else -> Availability(true, null)
        }
        SRC_PEDOMETER -> when {
            !isXiaomi() -> Availability(false, "Only available on Xiaomi phones")
            !hasMiuiPermission(ctx) -> Availability(false, "Needs the Xiaomi step permission")
            readFrom(ctx, SRC_PEDOMETER) == null -> Availability(false, "The Xiaomi pedometer returned no data")
            else -> Availability(true, null)
        }
        SRC_SENSOR -> when {
            !hasSensor(ctx) -> Availability(false, "This phone has no step counter sensor")
            !hasActivityPermission(ctx) -> Availability(false, "Needs the Physical activity permission")
            else -> Availability(true, null)
        }
        else -> {
            val any = SOURCES.any { availability(ctx, it).ok }
            if (any) Availability(true, null) else Availability(false, "No step source is available on this phone")
        }
    }

    fun allAvailability(ctx: Context): Map<String, Availability> =
        (listOf(SRC_AUTO) + SOURCES).associateWith { availability(ctx, it) }

    fun read(ctx: Context): StepReading? = try {
        val key = ModulePrefs(ctx).stepSource
        readFrom(ctx, key) ?: if (key != SRC_AUTO) readFrom(ctx, SRC_AUTO) else null
    } catch (e: Throwable) {
        CrashLog.append(ctx, "steps.read", e)
        null
    }
}

object StepStore {
    private fun sp(ctx: Context) =
        ctx.applicationContext.getSharedPreferences("steps_state", Context.MODE_PRIVATE)

    private fun dayKey(): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    @Synchronized
    fun onCounter(ctx: Context, v: Float) {
        val p = sp(ctx)
        val now = System.currentTimeMillis()
        val today = dayKey()
        var day = p.getString("day", "") ?: ""
        var base = p.getFloat("base", v)
        var carry = p.getFloat("carry", 0f)
        val last = p.getFloat("last", -1f)
        val lastAt = p.getLong("lastAt", 0L)

        if (day != today) {

            base = if (last >= 0f && v >= last && now - lastAt < 6 * 3_600_000L) last else v
            carry = 0f
            day = today
        } else if (last >= 0f && v < last) {

            carry += (last - base).coerceAtLeast(0f)
            base = 0f
        }
        p.edit().putString("day", day).putFloat("base", base).putFloat("carry", carry)
            .putFloat("last", v).putLong("lastAt", now).apply()
    }

    fun hasData(ctx: Context): Boolean = sp(ctx).getFloat("last", -1f) >= 0f

    fun today(ctx: Context): Int {
        val p = sp(ctx)
        if ((p.getString("day", "") ?: "") != dayKey()) return 0
        val v = p.getFloat("carry", 0f) + p.getFloat("last", 0f) - p.getFloat("base", 0f)
        return v.toInt().coerceAtLeast(0)
    }

    @Synchronized
    fun markGoalOnce(ctx: Context): Boolean {
        val p = sp(ctx)
        val today = dayKey()
        if (p.getString("goalDay", "") == today) return false
        p.edit().putString("goalDay", today).apply()
        return true
    }
}

class StepSensor(ctx: Context) : SensorEventListener {
    private val app = ctx.applicationContext
    private val sm = app.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor: Sensor? = sm?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private var registered = false

    @Synchronized
    fun start(): Boolean {
        if (registered) return true
        if (sensor == null || sm == null) return false
        if (!Steps.hasActivityPermission(app)) return false
        return try {
            registered = sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL, 0)
            registered
        } catch (e: Throwable) {
            CrashLog.append(app, "stepsensor.start", e)
            false
        }
    }

    @Synchronized
    fun stop() {
        if (!registered) return
        try { sm?.unregisterListener(this) } catch (e: Throwable) { }
        registered = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        try {
            if (event.values.isNotEmpty()) StepStore.onCounter(app, event.values[0])
        } catch (e: Throwable) { }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
