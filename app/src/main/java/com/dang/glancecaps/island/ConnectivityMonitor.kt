@file:Suppress("DEPRECATION")

package com.dang.glancecaps.island

import android.Manifest
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.media.AudioManager
import android.media.AudioDeviceInfo
import android.bluetooth.BluetoothClass
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
    private var lastBtAddress: String? = null
    private var lastBtName: String? = null
    private var lastBtAt = 0L
    private val pendingBt = HashMap<String, Runnable>()
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
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
                addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
                addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            }
            ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
            registerNetwork()
            running = true
        } catch (e: Throwable) {
            CrashLog.append(ctx, "connectivity.start", e)
            try { ctx.unregisterReceiver(receiver) } catch (t: Throwable) { }
            try {
                networkCallback?.let { ctx.getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) }
            } catch (t: Throwable) { }
            networkCallback = null
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
        pendingBt.clear()
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
                val device = deviceOf(intent) ?: return
                scheduleBluetooth(device, 1500L)
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                val device = deviceOf(intent) ?: return
                cancelBluetooth(device)
            }
            BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED -> {
                if (!p.connBluetooth || !p.connShowNames || !hasBluetoothPermission()) return
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, -1)
                if (state != BluetoothAdapter.STATE_CONNECTED) return
                val device = deviceOf(intent) ?: return
                scheduleBluetooth(device, 1500L)
            }
            WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                if (!p.connWifi || !p.connShowNames) return
                @Suppress("DEPRECATION")
                val info = intent.getParcelableExtra<android.net.NetworkInfo>(WifiManager.EXTRA_NETWORK_INFO)
                if (info?.isConnected == true && lastAirplane != true) scheduleSsid(350)
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

    private fun deviceOf(intent: Intent): BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33) {
        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
    } else {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
    }

    private fun addressOf(device: BluetoothDevice): String? = try {
        device.address
    } catch (e: Throwable) {
        null
    }

    private fun cancelBluetooth(device: BluetoothDevice) {
        val address = addressOf(device) ?: return
        pendingBt.remove(address)?.let { handler.removeCallbacks(it) }
    }

    private fun scheduleBluetooth(device: BluetoothDevice, delayMs: Long) {
        val address = addressOf(device) ?: return
        val bonded = try {
            device.bondState == BluetoothDevice.BOND_BONDED
        } catch (e: Throwable) {
            false
        }
        if (!bonded) return
        cancelBluetooth(device)
        val task = Runnable {
            pendingBt.remove(address)
            verifyBluetooth(device, 0)
        }
        pendingBt[address] = task
        handler.postDelayed(task, delayMs)
    }

    private fun verifyBluetooth(device: BluetoothDevice, attempt: Int) {
        try {
            if (!hasBluetoothPermission()) return
            val address = addressOf(device) ?: return
            if (audioConnected(address)) {
                announceBluetooth(device, 0)
                return
            }
            val audioClass = try {
                device.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO
            } catch (e: Throwable) {
                false
            }
            val limit = if (audioClass) 3 else 1
            if (attempt >= limit) {
                if (!audioClass) announceBluetooth(device, 0)
                return
            }
            val task = Runnable {
                pendingBt.remove(address)
                verifyBluetooth(device, attempt + 1)
            }
            pendingBt[address] = task
            handler.postDelayed(task, if (audioClass) 1500L else 2000L)
        } catch (e: Throwable) {
            CrashLog.append(ctx, "connectivity.bluetooth.verify", e)
        }
    }

    private fun audioConnected(address: String): Boolean = try {
        ctx.getSystemService(AudioManager::class.java)
            .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .any { isBluetoothAudio(it.type) && it.address.equals(address, ignoreCase = true) }
    } catch (e: Throwable) {
        false
    }

    private fun isBluetoothAudio(type: Int): Boolean =
        type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            type == AudioDeviceInfo.TYPE_HEARING_AID ||
            (Build.VERSION.SDK_INT >= 31 &&
                (type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER || type == AudioDeviceInfo.TYPE_BLE_BROADCAST))

    private fun announceBluetooth(device: BluetoothDevice, attempt: Int) {
        try {
            val p = ModulePrefs(ctx)
            if (!p.connBluetooth || !p.connShowNames || suppressed()) return
            val name = bluetoothName(device)
            val address = try { device.address } catch (e: Throwable) { null }
            if (!name.isNullOrBlank()) {
                val now = SystemClock.elapsedRealtime()
                if (address != null && address == lastBtAddress && name == lastBtName && now - lastBtAt < 20_000L) return
                lastBtAddress = address
                lastBtName = name
                lastBtAt = now
                post(ConnKind.BLUETOOTH, true, name, p)
            } else if (attempt < 8) {
                handler.postDelayed({ announceBluetooth(device, attempt + 1) }, 600)
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

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            if (lastSsid == null) scheduleSsid(300)
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
        } else if (++ssidTries < 20) {
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
        IslandPoster.post(ctx, GlanceCapsNotifications.ID_CONN, GlanceCapsNotifications.connectivity(ctx, kind, on, detail, p))
        cancelTask?.let { handler.removeCallbacks(it) }
        val task = Runnable { try { nm.cancel(GlanceCapsNotifications.ID_CONN) } catch (e: Throwable) { } }
        cancelTask = task
        val hold = GlanceCapsNotifications.connDurationMs(kind, on, detail, p) + 400L
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
