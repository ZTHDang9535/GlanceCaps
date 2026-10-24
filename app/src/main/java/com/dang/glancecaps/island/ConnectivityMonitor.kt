package com.dang.glancecaps.island

import android.Manifest
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.dang.glancecaps.CrashLog
import com.dang.glancecaps.data.ModulePrefs
import com.dang.glancecaps.island.GlanceCapsNotifications.ConnKind

class ConnectivityMonitor(context: Context) {
    private val ctx = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var lastWifi: Boolean? = null
    private var lastBluetooth: Boolean? = null
    private var lastAirplane: Boolean? = null
    private var airplaneChangedAt = 0L
    private var lastSsid: String? = null
    private var capSsid: String? = null
    private var ssidTries = 0
    private var cancelTask: Runnable? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            try {
                handle(intent)
            } catch (e: Throwable) {
                CrashLog.append(ctx, "connectivity.receive", e)
            }
        }
    }

    private val ssidTask = object : Runnable {
        override fun run() {
            try {
                resolveSsid()
            } catch (e: Throwable) {
                CrashLog.append(ctx, "connectivity.ssid", e)
            }
        }
    }

    fun start() {
        if (running) return
        try {
            lastWifi = readWifi()
            lastBluetooth = readBluetooth()
            lastAirplane = readAirplane()
            lastSsid = null
            capSsid = null
            val filter = IntentFilter().apply {
                addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            }
            ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
            registerNetwork()
            running = true
        } catch (e: Throwable) {
            CrashLog.append(ctx, "connectivity.start", e)
        }
    }

    fun stop() {
        if (!running) return
        running = false
        try { ctx.unregisterReceiver(receiver) } catch (e: Throwable) { }
        try {
            networkCallback?.let { ctx.getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) }
        } catch (e: Throwable) { }
        networkCallback = null
        handler.removeCallbacksAndMessages(null)
        cancelTask = null
        try { ctx.getSystemService(NotificationManager::class.java).cancel(GlanceCapsNotifications.ID_CONN) } catch (e: Throwable) { }
    }

    private fun suppressed(): Boolean =
        lastAirplane == true || SystemClock.elapsedRealtime() - airplaneChangedAt < AIRPLANE_QUIET_MS

    private fun handle(intent: Intent) {
        val p = ModulePrefs(ctx)
        when (intent.action) {
            WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                val state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)
                val on = when (state) {
                    WifiManager.WIFI_STATE_ENABLED -> true
                    WifiManager.WIFI_STATE_DISABLED -> false
                    else -> return
                }
                if (lastWifi == on) return
                lastWifi = on
                if (!on) {
                    lastSsid = null
                    capSsid = null
                    handler.removeCallbacks(ssidTask)
                }
                if (p.connWifi && !suppressed()) {
                    post(ConnKind.WIFI, on, null, p)
                    if (on) scheduleSsid(1200)
                }
            }
            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                val on = when (state) {
                    BluetoothAdapter.STATE_ON -> true
                    BluetoothAdapter.STATE_OFF -> false
                    else -> return
                }
                if (lastBluetooth == on) return
                lastBluetooth = on
                if (p.connBluetooth && !suppressed()) post(ConnKind.BLUETOOTH, on, null, p)
            }
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                if (!p.connBluetooth || !p.connShowNames || !hasBluetoothPermission()) return
                val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                if (device != null) announceBluetooth(device, 0)
            }
            Intent.ACTION_AIRPLANE_MODE_CHANGED -> {
                val on = intent.getBooleanExtra("state", false)
                if (lastAirplane == on) return
                lastAirplane = on
                airplaneChangedAt = SystemClock.elapsedRealtime()
                handler.removeCallbacks(ssidTask)
                if (p.connAirplane) post(ConnKind.AIRPLANE, on, null, p)
                if (!on && p.connWifi) scheduleSsid(AIRPLANE_QUIET_MS + 500)
            }
        }
    }

    private fun announceBluetooth(device: BluetoothDevice, attempt: Int) {
        try {
            val p = ModulePrefs(ctx)
            if (!p.connBluetooth || !p.connShowNames || suppressed()) return
            val name = bluetoothName(device)
            if (!name.isNullOrBlank()) {
                post(ConnKind.BLUETOOTH, true, name, p)
            } else if (attempt < 4) {
                handler.postDelayed({ announceBluetooth(device, attempt + 1) }, 700)
            }
        } catch (e: Throwable) {
            CrashLog.append(ctx, "connectivity.bluetooth", e)
        }
    }

    private fun bluetoothName(device: BluetoothDevice): String? = try {
        val alias = if (Build.VERSION.SDK_INT >= 30) device.alias else null
        if (!alias.isNullOrBlank()) alias else device.name
    } catch (e: Throwable) {
        null
    }

    private fun registerNetwork() {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        val cb: ConnectivityManager.NetworkCallback =
            if (Build.VERSION.SDK_INT >= 31) LocationAwareCallback() else LegacyCallback()
        cm.registerNetworkCallback(request, cb)
        networkCallback = cb
    }

    private inner class LegacyCallback : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            scheduleSsid(900)
        }

        override fun onLost(network: Network) {
            lastSsid = null
            capSsid = null
        }
    }

    @RequiresApi(31)
    private inner class LocationAwareCallback : ConnectivityManager.NetworkCallback(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO) {
        override fun onAvailable(network: Network) {
            scheduleSsid(900)
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val info = caps.transportInfo as? WifiInfo ?: return
            val ssid = cleanSsid(info.ssid) ?: return
            if (ssid != capSsid) {
                capSsid = ssid
                scheduleSsid(300)
            }
        }

        override fun onLost(network: Network) {
            lastSsid = null
            capSsid = null
        }
    }

    private fun scheduleSsid(delayMs: Long) {
        ssidTries = 0
        handler.removeCallbacks(ssidTask)
        handler.postDelayed(ssidTask, delayMs)
    }

    private fun resolveSsid() {
        val p = ModulePrefs(ctx)
        if (!p.connWifi || !p.connShowNames || !hasLocationPermission()) return
        if (lastAirplane == true) return
        if (suppressed()) {
            handler.postDelayed(ssidTask, 1000)
            return
        }
        val ssid = capSsid ?: readSsid()
        if (ssid != null) {
            if (ssid != lastSsid) {
                lastSsid = ssid
                post(ConnKind.WIFI, true, ssid, p)
            }
        } else if (++ssidTries < 10) {
            handler.postDelayed(ssidTask, 800)
        }
    }

    private fun cleanSsid(raw: String?): String? {
        val clean = raw?.removePrefix("\"")?.removeSuffix("\"") ?: return null
        return if (clean.isBlank() || clean == WifiManager.UNKNOWN_SSID) null else clean
    }

    @Suppress("DEPRECATION")
    private fun readSsid(): String? {
        val wm = ctx.getSystemService(WifiManager::class.java) ?: return null
        return cleanSsid(wm.connectionInfo?.ssid)
    }

    private fun readWifi(): Boolean = try {
        ctx.getSystemService(WifiManager::class.java)?.isWifiEnabled == true
    } catch (e: Throwable) { false }

    private fun readBluetooth(): Boolean = try {
        ctx.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
    } catch (e: Throwable) { false }

    private fun readAirplane(): Boolean = try {
        Settings.Global.getInt(ctx.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
    } catch (e: Throwable) { false }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun hasBluetoothPermission() =
        Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun post(kind: ConnKind, on: Boolean, detail: String?, p: ModulePrefs) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.notify(GlanceCapsNotifications.ID_CONN, GlanceCapsNotifications.connectivity(ctx, kind, on, detail, p))
        cancelTask?.let { handler.removeCallbacks(it) }
        val task = Runnable { try { nm.cancel(GlanceCapsNotifications.ID_CONN) } catch (e: Throwable) { } }
        cancelTask = task
        val hold = p.connSeconds.coerceIn(2, 10) * 1000L + 400L
        activeUntil = SystemClock.elapsedRealtime() + hold + 300L
        handler.postDelayed(task, hold)
    }

    companion object {
        private const val AIRPLANE_QUIET_MS = 6000L

        @Volatile
        private var activeUntil = 0L

        fun showing(): Boolean = SystemClock.elapsedRealtime() < activeUntil

        fun missingPermissions(ctx: Context): Array<String> {
            val need = ArrayList<String>()
            need.add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= 31) need.add(Manifest.permission.BLUETOOTH_CONNECT)
            return need.filter {
                ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED
            }.toTypedArray()
        }
    }
}
