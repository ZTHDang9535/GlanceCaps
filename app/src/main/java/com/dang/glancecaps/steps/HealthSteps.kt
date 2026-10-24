package com.dang.glancecaps.steps

import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.dang.glancecaps.CrashLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

object HealthSteps {
    const val NEEDS_PERMISSION = "Needs Health Connect permission"
    const val UNAVAILABLE = "Health Connect is not available on this phone"
    const val NO_DATA_MI_FITNESS = "Mi Fitness has not shared steps yet. In Mi Fitness open Profile, Third-party data, Health Connect"
    const val NO_DATA_ZEPP = "Zepp Life has not shared steps yet. Turn on Health Connect in Zepp Life or Zepp, then sync your band"

    private const val BACKGROUND = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"
    private val miFitness = setOf(DataOrigin("com.xiaomi.wearable"), DataOrigin("com.mi.health"))
    private val zepp = setOf(DataOrigin("com.xiaomi.hm.health"), DataOrigin("com.huami.watch.hmwatchmanager"))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private class Entry {
        @Volatile var steps: Int? = null
        @Volatile var at = 0L
        @Volatile var busy = false
    }

    private val entries = ConcurrentHashMap<String, Entry>()

    private fun origins(key: String): Set<DataOrigin> = if (key == Steps.SRC_ZEPP) zepp else miFitness

    fun noData(key: String): String = if (key == Steps.SRC_ZEPP) NO_DATA_ZEPP else NO_DATA_MI_FITNESS

    fun permissions(): Set<String> {
        val set = HashSet<String>()
        set.add(HealthPermission.getReadPermission(StepsRecord::class))
        if (Build.VERSION.SDK_INT >= 34) set.add(BACKGROUND)
        return set
    }

    fun sdkAvailable(ctx: Context): Boolean = try {
        HealthConnectClient.getSdkStatus(ctx) == HealthConnectClient.SDK_AVAILABLE
    } catch (e: Throwable) {
        false
    }

    suspend fun granted(ctx: Context): Boolean = try {
        HealthConnectClient.getOrCreate(ctx).permissionController.getGrantedPermissions()
            .contains(HealthPermission.getReadPermission(StepsRecord::class))
    } catch (e: Throwable) {
        false
    }

    suspend fun fetch(ctx: Context, key: String): Int? {
        val client = HealthConnectClient.getOrCreate(ctx)
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
        val request = AggregateRequest(
            metrics = setOf(StepsRecord.COUNT_TOTAL),
            timeRangeFilter = TimeRangeFilter.between(start, Instant.now()),
            dataOriginFilter = origins(key)
        )
        val value = client.aggregate(request)[StepsRecord.COUNT_TOTAL]?.toInt()
        val entry = entries.getOrPut(key) { Entry() }
        entry.steps = value
        entry.at = SystemClock.elapsedRealtime()
        return value
    }

    fun cached(ctx: Context, key: String): Int? {
        val entry = entries.getOrPut(key) { Entry() }
        val now = SystemClock.elapsedRealtime()
        if (!entry.busy && now - entry.at > 45_000L) {
            entry.busy = true
            val app = ctx.applicationContext
            scope.launch {
                try {
                    if (sdkAvailable(app) && granted(app)) fetch(app, key) else entry.steps = null
                } catch (e: Throwable) {
                    CrashLog.append(app, "healthsteps.fetch", e)
                } finally {
                    entry.at = SystemClock.elapsedRealtime()
                    entry.busy = false
                }
            }
        }
        return entry.steps
    }
}
